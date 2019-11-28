flume-ng-sql-source
================

针对[flume-ng-sql-source](https://github.com/keedio/flume-ng-sql-source)的开源项目进行修改,修改部分:  
- 更改输出格式为json
- 根据增量时间字段来进行增量查询数据,支持int型时间戳和1970-01-01 00:00:00字符串类型时间
- 增加全量和增量属性

Current sql database engines supported：

-------------------------------
- After the last update the code has been integrated with hibernate, so all databases supported by this technology should work.
- 理论上支持大多数数据源

Compilation and packaging
----------
```
  $ mvn package
```

Deployment
----------

Copy flume-ng-sql-source-<version>.jar in target folder into flume plugins dir folder
```
  $ mkdir -p $FLUME_HOME/plugins.d/sql-source/lib $FLUME_HOME/plugins.d/sql-source/libext
  $ cp flume-ng-sql-source-1.5.3.jar $FLUME_HOME/plugins.d/sql-source/lib
```

### Specific installation by database engine

##### Oracle
Download the official oracle jdbc driver and copy in libext flume plugins directory:
```
$ cp ojdbc8.jar $FLUME_HOME/plugins.d/sql-source/libext
```
注意:  
agent.sources.sqlSource.hibernate.connection.url = jdbc:oracle:thin:@127.0.0.1:1521/test
agent.sources.sqlSource.hibernate.dialect = org.hibernate.dialect.Oracle10gDialect
agent.sources.sqlSource.hibernate.connection.driver_class = oracle.jdbc.driver.OracleDriver
##### Sap HaNa
Download the official hana jdbc driver and copy in libext flume plugins directory:
```
$ cp ngdbc.jar $FLUME_HOME/plugins.d/sql-source/libext
```
注意:  
agent.sources.sqlSource.hibernate.connection.url = jdbc:sap://127.0.0.1:30015?autoReconnect=true
agent.sources.sqlSource.hibernate.dialect = org.hibernate.dialect.HANAColumnStoreDialect
agent.sources.sqlSource.hibernate.connection.driver_class = com.sap.db.jdbc.Driver

##### MySQL
Download the official mysql jdbc driver and copy in libext flume plugins directory:
```
$ wget https://dev.mysql.com/get/Downloads/Connector-J/mysql-connector-java-5.1.35.tar.gz
$ tar xzf mysql-connector-java-5.1.35.tar.gz
$ cp mysql-connector-java-5.1.35-bin.jar $FLUME_HOME/plugins.d/sql-source/libext
```
注意:  
agent.sources.sqlSource.hibernate.connection.url = jdbc:mysql://127.0.0.1:3306/test?autoReconnect=true
agent.sources.sqlSource.hibernate.dialect = org.hibernate.dialect.MySQL5Dialect
agent.sources.sqlSource.hibernate.connection.driver_class = com.mysql.jdbc.Driver

##### Microsoft SQLServer
Download the official Microsoft 4.1 Sql Server jdbc driver and copy in libext flume plugins directory:  
Download URL: https://www.microsoft.com/es-es/download/details.aspx?id=11774  
```
$ tar xzf sqljdbc_4.1.5605.100_enu.tar.gz
$ cp sqljdbc_4.1/enu/sqljdbc41.jar $FLUME_HOME/plugins.d/sql-source/libext
```
注意:  
agent.sources.sqlSource.hibernate.connection.url = jdbc:sqlserver://127.0.0.1:1433;DatabaseName=test;autoReconnect=true
agent.sources.sqlSource.hibernate.dialect = org.victor.flume.source.SQLServerCustomDialect
agent.sources.sqlSource.hibernate.connection.driver_class = com.microsoft.sqlserver.jdbc.SQLServerDriver

##### IBM DB2
Download the official IBM DB2 jdbc driver and copy in libext flume plugins directory:
Download URL: http://www-01.ibm.com/support/docview.wss?uid=swg21363866

Configuration of SQL Source:
----------
Mandatory properties in <b>bold</b>

| Property Name | Default | Description |
| ----------------------- | :-----: | :---------- |
| <b>channels</b> | - | Connected channel names |
| <b>type</b> | - | The component type name, needs to be org.victor.flume.source.SQLSource  |
| <b>hibernate.connection.url</b> | - | Url to connect with the remote Database |
| <b>hibernate.connection.user</b> | - | Username to connect with the database |
| <b>hibernate.connection.password</b> | - | Password to connect with the database |
| <b>table</b> | - | Table to export data |
| <b>status.file.name</b> | - | Local file name to save last row number read |
| status.file.path | /var/lib/flume | Path to save the status file |
| start.from | 0 | Start value to import data |
| delimiter.entry | , | delimiter of incoming entry | 
| enclose.by.quotes | true | If Quotes are applied to all values in the output. |
| columns.to.select | * | Which colums of the table will be selected |
| run.query.delay | 10000 | ms to wait between run queries |
| batch.size| 100 | Batch size to send events to flume channel |
| max.rows | 10000| Max rows to import per query |
| read.only | false| Sets read only session with DDBB |
| custom.query | - | Custom query to force a special request to the DB, be carefull. Check below explanation of this property. |
| hibernate.connection.driver_class | -| Driver class to use by hibernate, if not specified the framework will auto asign one |
| hibernate.dialect | - | Dialect to use by hibernate, if not specified the framework will auto asign one. Check https://docs.jboss.org/hibernate/orm/4.3/manual/en-US/html/ch03.html#configuration-optional-dialects for a complete list of available dialects |
| hibernate.connection.provider_class | - | Set to org.hibernate.connection.C3P0ConnectionProvider to use C3P0 connection pool (recommended for production) |
| hibernate.c3p0.min_size | - | Min connection pool size |
| hibernate.c3p0.max_size | - | Max connection pool size |
| default.charset.resultset | UTF-8 | Result set from DB converted to charset character encoding |
| time.column | - | 时间列 |  
| time.column.type | - | 时间列类型int/string |  
| source.transfer.method | - | 数据采集方式,增量/全量 |  
| source.db.type | - | 数据源类型(mysql/sqlserver/oracle/Hana),当前oracle语法与其他数据源不一致时使用 |


Standard Query
-------------
If no custom query is set, ```SELECT <columns.to.select> FROM <table>``` will be executed each ```run.query.delay``` milliseconds configured

Custom Query
-------------
A custom query is supported to bring the possibility of using the entire SQL language. This is powerful, but risky, be careful with the custom queries used.  

To avoid row export repetitions use the $@$ special character in WHERE clause, to incrementaly export not processed rows and the new ones inserted.

IMPORTANT: For proper operation of Custom Query ensure that incremental field will be returned in the first position of the Query result.

Example:
```
agent.sources.sql-source.custom.query = SELECT incrementalField,field2 FROM table1 WHERE incrementalField > $@$ 
```

Configuration example
--------------------

```properties
# For each one of the sources, the type is defined
agent.channels = memoryChannel
agent.sources = sqlSource
agent.sinks = kafkaSink


# For each one of the sources, the type is defined
agent.sources.sqlSource.type = org.victor.flume.source.SQLSource

agent.sources.sqlSource.hibernate.connection.url = jdbc:mysql://127.0.0.1:3306/test?autoReconnect=true

# Hibernate Database connection properties
agent.sources.sqlSource.hibernate.connection.user = test
agent.sources.sqlSource.hibernate.connection.password = test
agent.sources.sqlSource.hibernate.connection.autocommit = true
agent.sources.sqlSource.hibernate.dialect = org.hibernate.dialect.MySQL5Dialect
agent.sources.sqlSource.hibernate.connection.driver_class = com.mysql.jdbc.Driver
agent.sources.sqlSource.hibernate.temp.use_jdbc_metadata_defaults=false
agent.sources.sqlSource.hibernate.connection.provider_class = org.hibernate.connection.C3P0ConnectionProvider
agent.sources.sqlSource.hibernate.c3p0.max_size = 5
agent.sources.sqlSource.hibernate.c3p0.min_size = 3
agent.sources.sqlSource.hibernate.c3p0.timeout = 5000
# 此处max_statements修改为0
agent.sources.sqlSource.hibernate.c3p0.max_statements = 0
agent.sources.sqlSource.hibernate.c3p0.idle_test_period = 3000
agent.sources.sqlSource.hibernate.c3p0.acquire_increment = 1
agent.sources.sqlSource.hibernate.c3p0.validate = true

agent.sources.sqlSource.table = test
# Columns to import to kafka (default * import entire row)
agent.sources.sqlSource.columns.to.select = *
# Query delay, each configured milisecond the query will be sent
agent.sources.sqlSource.run.query.delay=60000
# Status file is used to save last readed row
agent.sources.sqlSource.status.file.path = /data/flume_status
agent.sources.sqlSource.status.file.name = test

# Custom query
agent.sources.sqlSource.start.from = 2019-07-03 14:52:00
agent.sources.sqlSource.time.column = updateTime
agent.sources.sqlSource.time.column.type = string
agent.sources.sqlSource.source.transfer.method = incrementing

agent.sources.sqlSource.batch.size = 2000
agent.sources.sqlSource.max.rows = 3000


agent.channels.memoryChannel.type = memory
agent.channels.memoryChannel.capacity = 4000
agent.channels.memoryChannel.transactionCapacity = 2000
agent.channels.memoryChannel.byteCapacityBufferPercentage = 20

agent.channels.memoryChannel.keep-alive = 60
agent.channels.memoryChannel.capacity = 1000000
agent.sources.sqlSource.channels = memoryChannel
agent.sinks.kafkaSink.channel = memoryChannel

######配置kafka sink ##############################
agent.sinks.kafkaSink.type = org.apache.flume.sink.kafka.KafkaSink
#kafka话题名称
agent.sinks.kafkaSink.kafka.topic = test
#kafka的地址配置
agent.sinks.kafkaSink.kafka.bootstrap.servers = 127.0.0.1:9092
#设置序列化方式
agent.sinks.kafkaSink.serializer.class=kafka.serializer.StringEncoder
agent.sinks.kafkaSink.kafka.producer.acks = 1
agent.sinks.kafkaSink.flumeBatchSize = 100
```

```file channel
agent.channels = fileChannel
agent.sources = sqlSource
agent.sinks = kuduSink


# For each one of the sources, the type is defined
agent.sources.sqlSource.type = org.victor.flume.source.SQLSource

agent.sources.sqlSource.hibernate.connection.url = jdbc:mysql://127.0.0.1:3306/test?autoReconnect=true

# Hibernate Database connection properties
agent.sources.sqlSource.hibernate.connection.user =test 
agent.sources.sqlSource.hibernate.connection.password = test
agent.sources.sqlSource.hibernate.connection.autocommit = true
agent.sources.sqlSource.hibernate.dialect = org.hibernate.dialect.MySQL5Dialect
agent.sources.sqlSource.hibernate.connection.driver_class = com.mysql.jdbc.Driver
agent.sources.sqlSource.hibernate.temp.use_jdbc_metadata_defaults=false
agent.sources.sqlSource.hibernate.c3p0.max_size = 5
agent.sources.sqlSource.hibernate.c3p0.min_size = 3
agent.sources.sqlSource.hibernate.c3p0.timeout = 5000
agent.sources.sqlSource.hibernate.c3p0.max_statements = 10
agent.sources.sqlSource.hibernate.c3p0.idle_test_period = 3000
agent.sources.sqlSource.hibernate.c3p0.acquire_increment = 1
agent.sources.sqlSource.hibernate.c3p0.validate = true

agent.sources.sqlSource.table = test
agent.sources.sqlSource.custom.query = select * from test.test where updateTime > '$@$' and updateTime <= '$&$'
# Query delay, each configured milisecond the query will be sent
agent.sources.sqlSource.run.query.delay=60000
# Status file is used to save last readed row
agent.sources.sqlSource.status.file.path = /data/flume_status
agent.sources.sqlSource.status.file.name = test

# Custom query
agent.sources.sqlSource.start.from = 2019-09-25 00:00:00
agent.sources.sqlSource.time.column = updateTime
agent.sources.sqlSource.time.column.type = string
agent.sources.sqlSource.source.transfer.method = incrementing

agent.sources.sqlSource.batch.size = 3000
agent.sources.sqlSource.max.rows = 1000000

agent.sources.sqlSource.hibernate.connection.provider_class = org.hibernate.connection.C3P0ConnectionProvider
agent.sources.sqlSource.hibernate.c3p0.min_size=1
agent.sources.sqlSource.hibernate.c3p0.max_size=3

# The channel can be defined as follows.
agent.channels.fileChannel.type = file
agent.channels.fileChannel.checkpointDir = /data/flume_checkpont/test
agent.channels.fileChannel.dataDirs = /data/flume_datadirs/test
agent.channels.fileChannel.capacity = 200000000

agent.channels.fileChannel.keep-alive = 180
agent.channels.fileChannel.write-timeout = 180
agent.channels.fileChannel.checkpoint-timeout = 300
agent.channels.fileChannel.transactionCapacity = 1000000

agent.sources.sqlSource.channels = fileChannel
agent.sinks.kuduSink.channel = fileChannel

######配置kudu sink ##############################
agent.sinks.kuduSink.type = com.flume.sink.kudu.KuduSink
agent.sinks.kuduSink.masterAddresses = 192.168.0.1:7051,192.168.0.2:7051,192.168.0.3:7051
agent.sinks.kuduSink.customKey = order_no
agent.sinks.kuduSink.tableName = test
agent.sinks.kuduSink.batchSize = 3000
agent.sinks.kuduSink.namespace = test
agent.sinks.kuduSink.producer.operation = upsert
agent.sinks.kuduSink.producer = com.flume.sink.kudu.JsonKuduOperationProducer

```

Known Issues
---------
An issue with Java SQL Types and Hibernate Types could appear Using SQL Server databases and SQL Server Dialect coming with Hibernate.  
  
Something like:
```
org.hibernate.MappingException: No Dialect mapping for JDBC type: -15
```

Use ```org.victor.flume.source.SQLServerCustomDialect``` in flume configuration file to solve this problem.

Special thanks
---------------
Thanks to [flume-ng-sql-source](https://github.com/keedio/flume-ng-sql-source)
