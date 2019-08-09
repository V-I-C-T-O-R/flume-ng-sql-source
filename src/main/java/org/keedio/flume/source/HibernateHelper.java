package org.keedio.flume.source;

import java.text.SimpleDateFormat;
import java.util.*;

import org.hibernate.CacheMode;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.service.ServiceRegistryBuilder;
import org.hibernate.transform.Transformers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.flume.Context;

/**
 * Helper class to manage hibernate sessions and perform queries
 * 
 * @author <a href="mailto:mvalle@keedio.com">Marcelo Valle</a>
 *
 */
public class HibernateHelper {

	private static final Logger LOG = LoggerFactory
			.getLogger(HibernateHelper.class);

	private static SessionFactory factory;
	private Session session;
	private Configuration config;
	private SQLSourceHelper sqlSourceHelper;
	private ServiceRegistry serviceRegistry;

	/**
	 * Constructor to initialize hibernate configuration parameters
	 * @param sqlSourceHelper Contains the configuration parameters from flume config file
	 */
	public HibernateHelper(SQLSourceHelper sqlSourceHelper) {

		this.sqlSourceHelper = sqlSourceHelper;
		Context context = sqlSourceHelper.getContext();

		Map<String,String> hibernateProperties = context.getSubProperties("hibernate.");
		Iterator<Map.Entry<String,String>> it = hibernateProperties.entrySet().iterator();

		config = new Configuration();
		Map.Entry<String, String> e;

		while (it.hasNext()){
			e = it.next();
			config.setProperty("hibernate." + e.getKey(), e.getValue());
		}
	}

	/**
	 * Connect to database using hibernate
	 */
	public void establishSession() {

		LOG.info("Opening hibernate session");
		serviceRegistry = new ServiceRegistryBuilder().applySettings(config.getProperties()).buildServiceRegistry();
		factory = config.buildSessionFactory(serviceRegistry);

		session = factory.openSession();
		session.setCacheMode(CacheMode.IGNORE);

		session.setDefaultReadOnly(sqlSourceHelper.isReadOnlySession());
	}

	/**
	 * Close database connection
	 */
	public void closeSession() {

		LOG.info("Closing hibernate session");
		//如果session获取通过getCurrentSession()获得的Session提交时自动关闭，
		// 其不用在session.close(),如果在调用session.close().其相当于session关闭两次 ,
		// 所以会出现Session was already closed异常
		if(session.isOpen() || session.isConnected())
			session.close();
		factory.close();
	}

	/**
	 * Execute the selection query in the database
	 * @return The query result. Each Object is a cell content. <p>
	 * The cell contents use database types (date,int,string...),
	 * keep in mind in case of future conversions/castings.
	 * @throws InterruptedException
	 */
	@SuppressWarnings("unchecked")
	public List<Map<String,Object>> executeQuery() throws InterruptedException {

		List<Map<String,Object>> rowsList = new ArrayList<Map<String,Object>>() ;
		Query query;

		if (!session.isConnected()){
			resetConnection();
		}

		String maxTime = "";
		List<List<Object>> max = null;
		if(sqlSourceHelper.isTransferIncrement()){
			try{
				String sql = sqlSourceHelper.maxQuery();
				max = session.createSQLQuery(sql).setResultTransformer(Transformers.TO_LIST).list();
				maxTime = max.get(0).get(0).toString();
				if(!sqlSourceHelper.isTimeColumnIntType()) {
					maxTime = maxTime.substring(0,19);
				}
			}catch (Exception e){
				resetConnection();
			}
			if("".equals(maxTime)){
				//服务器时钟慢了5分钟,笑哭
				Date now = new Date(new Date().getTime()+300000);

				if (SQLSourceHelper.TIME_COLUMN_TYPE_INT.equals(sqlSourceHelper.getTimeColumnType())) {
					maxTime = String.valueOf(now.getTime() / 1000);
				}else{
					maxTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(now);
				}
			}
			LOG.info("最大时间戳:"+maxTime);
		}else{
			LOG.info("全量模式");
		}

		try {
			String executSql = sqlSourceHelper.buildQuery(maxTime);
			LOG.info("执行sql:"+executSql);
			if (sqlSourceHelper.isCustomQuerySet()){
				query = session.createSQLQuery(executSql);
				if (sqlSourceHelper.getMaxRows() != 0){
					query = query.setMaxResults(sqlSourceHelper.getMaxRows());
				}
			}
			else
			{
				query = session
						.createSQLQuery(executSql);
//					.setFirstResult(Integer.parseInt(sqlSourceHelper.getCurrentIndex()));
				if (sqlSourceHelper.getMaxRows() != 0){
					query = query.setMaxResults(sqlSourceHelper.getMaxRows());
				}
			}

			rowsList = query.setFetchSize(sqlSourceHelper.getMaxRows()).setResultTransformer(Transformers.ALIAS_TO_ENTITY_MAP).list();
			//rowsList = query.setFetchSize(sqlSourceHelper.getMaxRows()).setResultTransformer(Transformers.TO_LIST).list();
			LOG.info("开始时间:"+sqlSourceHelper.getCurrentIndex()+",截止时间:"+maxTime+",数据量: "+String.valueOf(rowsList.size()));
		}catch (Exception e){
			LOG.error("Exception reset connection.",e);
			resetConnection();
		}

		if (!rowsList.isEmpty()&&sqlSourceHelper.isTransferIncrement()) {
//				sqlSourceHelper.setCurrentIndex(Integer.toString((Integer.parseInt(sqlSourceHelper.getCurrentIndex())
//						+ rowsList.size())));
			sqlSourceHelper.setCurrentIndex(maxTime);
		}

		return rowsList;
	}

	private void resetConnection() throws InterruptedException{
		session.close();
		factory.close();
		establishSession();
	}
}
