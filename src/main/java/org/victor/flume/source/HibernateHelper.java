package org.victor.flume.source;

import java.util.*;

import org.hibernate.*;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.transform.Transformers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.flume.Context;

/**
 * Helper class to manage hibernate sessions and perform queries
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
	 *
	 * @param sqlSourceHelper Contains the configuration parameters from flume config file
	 */
	public HibernateHelper(SQLSourceHelper sqlSourceHelper) {

		this.sqlSourceHelper = sqlSourceHelper;
		Context context = sqlSourceHelper.getContext();

		Map<String, String> hibernateProperties = context.getSubProperties("hibernate.");
		Iterator<Map.Entry<String, String>> it = hibernateProperties.entrySet().iterator();

		config = new Configuration();
		Map.Entry<String, String> e;

		while (it.hasNext()) {
			e = it.next();
			config.setProperty("hibernate." + e.getKey(), e.getValue());
		}

		//serviceRegistry = new ServiceRegistryBuilder().applySettings(config.getProperties()).buildServiceRegistry();
		/*
		 *new ServiceRegistryBuilder()此方法已废弃，可使用下面的方式获取ServiceRegistry
		 */
//		serviceRegistry = new StandardServiceRegistryBuilder().applySettings(config.getProperties()).build();
//		factory = config.buildSessionFactory(serviceRegistry);
	}

	/**
	 * Connect to database using hibernate
	 */
	public void establishSession() throws InterruptedException {

		LOG.info("Opening hibernate session");
		if (factory == null || factory.isClosed()) {
			serviceRegistry = new StandardServiceRegistryBuilder().applySettings(config.getProperties()).build();
			factory = config.buildSessionFactory(serviceRegistry);
		}

		LOG.info("hibernate factory ready to open session");
		if (session == null || !session.isOpen() || !session.isConnected())
			session = factory.openSession();
		session.setCacheMode(CacheMode.IGNORE);
		session.setDefaultReadOnly(sqlSourceHelper.isReadOnlySession());
		LOG.info("Open hibernate session finished");
	}

	/**
	 * Close database connection
	 */
	public void closeSession() {

		LOG.info("Closing hibernate session");
		try {
			if (session != null && (session.isOpen() || session.isConnected()))
				session.close();
			LOG.info("Close hibernate session finished");
		} catch (HibernateException e) {
			LOG.error("close session resources error", e);
		} catch (Exception e) {
			LOG.error("close resources error", e);
		}
		closeFactory();
	}

	public void closeFactory() {
		LOG.info("Closing hibernate factory");
		try {
			if (factory != null && !factory.isClosed())
				factory.close();
			LOG.info("Closing hibernate factory finished");
		} catch (HibernateException e) {
			LOG.error("close session factory error", e);
		} catch (Exception e) {
			LOG.error("close factory error", e);
		}
	}

	/**
	 * Execute the selection query in the database
	 *
	 * @return The query result. Each Object is a cell content. <p>
	 * The cell contents use database types (date,int,string...),
	 * keep in mind in case of future conversions/castings.
	 * @throws InterruptedException
	 */
	@SuppressWarnings("unchecked")
	public List<Map<String, Object>> executeQuery() throws InterruptedException {

		List<Map<String, Object>> rowsList = new ArrayList<Map<String, Object>>();
		Query query;

		if (!session.isConnected() || !session.isOpen()) {
			resetConnection();
		}

		String maxTime = "";
		List<List<Object>> max = null;
		if (sqlSourceHelper.isTransferIncrement()) {
			try {
				String sql = sqlSourceHelper.maxQuery();
				LOG.info("执行查询max时间sql:" + sql);
				max = session.createSQLQuery(sql).setResultTransformer(Transformers.TO_LIST).list();
				maxTime = max.get(0).get(0).toString();
//				if(!sqlSourceHelper.isTimeColumnIntType()) {
//					maxTime = maxTime.substring(0,19);
//				}
			} catch (Exception e) {
				LOG.info("执行查询max时间异常,连接被重置:", e);
				resetConnection();
				return rowsList;
			}

			LOG.info("最大时间戳:" + maxTime);
		} else {
			LOG.info("全量模式");
		}

		try {
			//为了业务里面数据误差,延迟1秒
			Thread.sleep(1000);
			String executSql = sqlSourceHelper.buildQuery(maxTime);
			LOG.info("执行sql:" + executSql);
			if (sqlSourceHelper.isCustomQuerySet()) {
				query = session.createSQLQuery(executSql);
				if (sqlSourceHelper.getMaxRows() != 0) {
					query = query.setMaxResults(sqlSourceHelper.getMaxRows());
				}
			} else {
				query = session
						.createSQLQuery(executSql);
//					.setFirstResult(Integer.parseInt(sqlSourceHelper.getCurrentIndex()));
				if (sqlSourceHelper.getMaxRows() != 0) {
					query = query.setMaxResults(sqlSourceHelper.getMaxRows());
				}
			}

			rowsList = query.setFetchSize(sqlSourceHelper.getMaxRows()).setResultTransformer(Transformers.ALIAS_TO_ENTITY_MAP).list();
			//rowsList = query.setFetchSize(sqlSourceHelper.getMaxRows()).setResultTransformer(Transformers.TO_LIST).list();
			LOG.info("开始时间:" + sqlSourceHelper.getCurrentIndex() + ",截止时间:" + maxTime + ",数据量: " + String.valueOf(rowsList.size()));
		} catch (Exception e) {
			LOG.error("Exception reset connection.", e);
			resetConnection();
		}

		if (!rowsList.isEmpty() && sqlSourceHelper.isTransferIncrement()) {
//				sqlSourceHelper.setCurrentIndex(Integer.toString((Integer.parseInt(sqlSourceHelper.getCurrentIndex())
//						+ rowsList.size())));
			sqlSourceHelper.setCurrentIndex(maxTime);
		}

		return rowsList;
	}

	private void resetConnection() throws InterruptedException {
		closeSession();
		establishSession();
	}
}
