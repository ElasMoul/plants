2026-06-14 07:00:44 [http-nio-8080-exec-4] [e36f4335-cd33-44b7-bf7d-4bc0621a9371] DEBUG o.s.w.s.m.m.a.HttpEntityMethodProcessor - Writing [com.plantpal.shared.dto.ApiResponse@76c18051]
2026-06-14 08:00:44 2026-06-14 07:00:44 [http-nio-8080-exec-4] [e36f4335-cd33-44b7-bf7d-4bc0621a9371] DEBUG o.s.web.servlet.DispatcherServlet - Completed 200 OK
2026-06-14 08:00:54 2026-06-14 07:00:54 [http-nio-8080-exec-6] [5f903ed5-cf46-4d70-b92d-fc1c9059eee2] DEBUG o.s.security.web.FilterChainProxy - Securing POST /api/v1/identifications/analyze
2026-06-14 08:00:54 2026-06-14 07:00:54 [http-nio-8080-exec-6] [5f903ed5-cf46-4d70-b92d-fc1c9059eee2] DEBUG o.s.security.web.FilterChainProxy - Secured POST /api/v1/identifications/analyze
2026-06-14 08:00:54 2026-06-14 07:00:54 [http-nio-8080-exec-6] [5f903ed5-cf46-4d70-b92d-fc1c9059eee2] DEBUG o.s.web.servlet.DispatcherServlet - POST "/api/v1/identifications/analyze", parameters={multipart}
2026-06-14 08:00:54 2026-06-14 07:00:54 [http-nio-8080-exec-6] [5f903ed5-cf46-4d70-b92d-fc1c9059eee2] DEBUG o.s.w.s.m.m.a.RequestMappingHandlerMapping - Mapped to com.plantpal.identification.controller.IdentificationController#analyze(List, List, Long)
2026-06-14 08:00:54 2026-06-14 07:00:54 [http-nio-8080-exec-6] [5f903ed5-cf46-4d70-b92d-fc1c9059eee2] INFO  c.p.i.c.IdentificationController - Identification requested: userId=1, images=1, plantId=null
2026-06-14 08:00:54 2026-06-14 07:00:54 [ai-task-1] [] DEBUG c.p.s.s.LocalFileStorageService - Saved photo: /tmp/plantpal/photos/ef653986-aacb-43be-893f-4a0a97823c88.jpg
2026-06-14 08:00:54 2026-06-14 07:00:54 [ai-task-1] [] WARN  o.h.e.jdbc.spi.SqlExceptionHelper - SQL Error: 0, SQLState: 42804
2026-06-14 08:00:54 2026-06-14 07:00:54 [ai-task-1] [] ERROR o.h.e.jdbc.spi.SqlExceptionHelper - ERROR: column "care_plan" is of type jsonb but expression is of type character varying
2026-06-14 08:00:54   Hint: You will need to rewrite or cast the expression.
2026-06-14 08:00:54   Position: 186
2026-06-14 08:00:55 2026-06-14 07:00:55 [ai-task-1] [] ERROR c.p.i.s.i.IdentificationServiceImpl - Identification failed for userId=1
2026-06-14 08:00:55 org.springframework.dao.InvalidDataAccessResourceUsageException: could not execute statement [ERROR: column "care_plan" is of type jsonb but expression is of type character varying
2026-06-14 08:00:55   Hint: You will need to rewrite or cast the expression.
2026-06-14 08:00:55   Position: 186] [insert into public.identifications (care_plan,common_name,confidence,created_at,created_by,photo_url,plant_id,raw_response,scientific_name,status,updated_at,updated_by,user_id) values (?,?,?,?,?,?,?,?,?,?,?,?,?)]; SQL [insert into public.identifications (care_plan,common_name,confidence,created_at,created_by,photo_url,plant_id,raw_response,scientific_name,status,updated_at,updated_by,user_id) values (?,?,?,?,?,?,?,?,?,?,?,?,?)]
2026-06-14 08:00:55     at org.springframework.orm.jpa.vendor.HibernateJpaDialect.convertHibernateAccessException(HibernateJpaDialect.java:277)
2026-06-14 08:00:55     at org.springframework.orm.jpa.vendor.HibernateJpaDialect.translateExceptionIfPossible(HibernateJpaDialect.java:241)
2026-06-14 08:00:55     at org.springframework.orm.jpa.AbstractEntityManagerFactoryBean.translateExceptionIfPossible(AbstractEntityManagerFactoryBean.java:550)
2026-06-14 08:00:55     at org.springframework.dao.support.ChainedPersistenceExceptionTranslator.translateExceptionIfPossible(ChainedPersistenceExceptionTranslator.java:61)
2026-06-14 08:00:55     at org.springframework.dao.support.DataAccessUtils.translateIfNecessary(DataAccessUtils.java:335)
2026-06-14 08:00:55     at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:152)
2026-06-14 08:00:55     at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-06-14 08:00:55     at org.springframework.data.jpa.repository.support.CrudMethodMetadataPostProcessor$CrudMethodMetadataPopulatingMethodInterceptor.invoke(CrudMethodMetadataPostProcessor.java:164)
2026-06-14 08:00:55     at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-06-14 08:00:55     at org.springframework.aop.interceptor.ExposeInvocationInterceptor.invoke(ExposeInvocationInterceptor.java:97)
2026-06-14 08:00:55     at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-06-14 08:00:55     at org.springframework.aop.framework.JdkDynamicAopProxy.invoke(JdkDynamicAopProxy.java:223)
2026-06-14 08:00:55     at jdk.proxy2/jdk.proxy2.$Proxy190.save(Unknown Source)
2026-06-14 08:00:55     at com.plantpal.identification.service.impl.IdentificationServiceImpl.identify(IdentificationServiceImpl.java:107)
2026-06-14 08:00:55     at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(Unknown Source)
2026-06-14 08:00:55     at java.base/java.lang.reflect.Method.invoke(Unknown Source)
2026-06-14 08:00:55     at org.springframework.aop.support.AopUtils.invokeJoinpointUsingReflection(AopUtils.java:354)
2026-06-14 08:00:55     at org.springframework.aop.framework.ReflectiveMethodInvocation.invokeJoinpoint(ReflectiveMethodInvocation.java:196)
2026-06-14 08:00:55     at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:163)
2026-06-14 08:00:55     at org.springframework.aop.interceptor.AsyncExecutionInterceptor.lambda$invoke$0(AsyncExecutionInterceptor.java:113)
2026-06-14 08:00:55     at org.springframework.util.concurrent.FutureUtils.lambda$toSupplier$0(FutureUtils.java:74)
2026-06-14 08:00:55     at java.base/java.util.concurrent.CompletableFuture$AsyncSupply.run(Unknown Source)
2026-06-14 08:00:55     at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(Unknown Source)
2026-06-14 08:00:55     at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(Unknown Source)
2026-06-14 08:00:55     at java.base/java.lang.Thread.run(Unknown Source)
2026-06-14 08:00:55 Caused by: org.hibernate.exception.SQLGrammarException: could not execute statement [ERROR: column "care_plan" is of type jsonb but expression is of type character varying
2026-06-14 08:00:55   Hint: You will need to rewrite or cast the expression.
2026-06-14 08:00:55   Position: 186] [insert into public.identifications (care_plan,common_name,confidence,created_at,created_by,photo_url,plant_id,raw_response,scientific_name,status,updated_at,updated_by,user_id) values (?,?,?,?,?,?,?,?,?,?,?,?,?)]
2026-06-14 08:00:55     at org.hibernate.exception.internal.SQLStateConversionDelegate.convert(SQLStateConversionDelegate.java:91)
2026-06-14 08:00:55     at org.hibernate.exception.internal.StandardSQLExceptionConverter.convert(StandardSQLExceptionConverter.java:58)
2026-06-14 08:00:55     at org.hibernate.engine.jdbc.spi.SqlExceptionHelper.convert(SqlExceptionHelper.java:108)
2026-06-14 08:00:55     at org.hibernate.engine.jdbc.internal.ResultSetReturnImpl.executeUpdate(ResultSetReturnImpl.java:197)
2026-06-14 08:00:55     at org.hibernate.id.insert.GetGeneratedKeysDelegate.performInsert(GetGeneratedKeysDelegate.java:107)
2026-06-14 08:00:55     at org.hibernate.engine.jdbc.mutation.internal.MutationExecutorPostInsertSingleTable.execute(MutationExecutorPostInsertSingleTable.java:100)
2026-06-14 08:00:55     at org.hibernate.persister.entity.mutation.InsertCoordinator.doStaticInserts(InsertCoordinator.java:175)
2026-06-14 08:00:55     at org.hibernate.persister.entity.mutation.InsertCoordinator.coordinateInsert(InsertCoordinator.java:113)
2026-06-14 08:00:55     at org.hibernate.persister.entity.AbstractEntityPersister.insert(AbstractEntityPersister.java:2868)
2026-06-14 08:00:55     at org.hibernate.action.internal.EntityIdentityInsertAction.execute(EntityIdentityInsertAction.java:81)
2026-06-14 08:00:55     at org.hibernate.engine.spi.ActionQueue.execute(ActionQueue.java:670)
2026-06-14 08:00:55     at org.hibernate.engine.spi.ActionQueue.addResolvedEntityInsertAction(ActionQueue.java:291)
2026-06-14 08:00:55     at org.hibernate.engine.spi.ActionQueue.addInsertAction(ActionQueue.java:272)
2026-06-14 08:00:55     at org.hibernate.engine.spi.ActionQueue.addAction(ActionQueue.java:322)
2026-06-14 08:00:55     at org.hibernate.event.internal.AbstractSaveEventListener.addInsertAction(AbstractSaveEventListener.java:386)
2026-06-14 08:00:55     at org.hibernate.event.internal.AbstractSaveEventListener.performSaveOrReplicate(AbstractSaveEventListener.java:300)
2026-06-14 08:00:55     at org.hibernate.event.internal.AbstractSaveEventListener.performSave(AbstractSaveEventListener.java:219)
2026-06-14 08:00:55     at org.hibernate.event.internal.AbstractSaveEventListener.saveWithGeneratedId(AbstractSaveEventListener.java:134)
2026-06-14 08:00:55     at org.hibernate.event.internal.DefaultPersistEventListener.entityIsTransient(DefaultPersistEventListener.java:175)
2026-06-14 08:00:55     at org.hibernate.event.internal.DefaultPersistEventListener.persist(DefaultPersistEventListener.java:93)
2026-06-14 08:00:55     at org.hibernate.event.internal.DefaultPersistEventListener.onPersist(DefaultPersistEventListener.java:77)
2026-06-14 08:00:55     at org.hibernate.event.internal.DefaultPersistEventListener.onPersist(DefaultPersistEventListener.java:54)
2026-06-14 08:00:55     at org.hibernate.event.service.internal.EventListenerGroupImpl.fireEventOnEachListener(EventListenerGroupImpl.java:127)
2026-06-14 08:00:55     at org.hibernate.internal.SessionImpl.firePersist(SessionImpl.java:754)
2026-06-14 08:00:55     at org.hibernate.internal.SessionImpl.persist(SessionImpl.java:738)
2026-06-14 08:00:55     at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(Unknown Source)
2026-06-14 08:00:55     at java.base/java.lang.reflect.Method.invoke(Unknown Source)
2026-06-14 08:00:55     at org.springframework.orm.jpa.SharedEntityManagerCreator$SharedEntityManagerInvocationHandler.invoke(SharedEntityManagerCreator.java:319)
2026-06-14 08:00:55     at jdk.proxy2/jdk.proxy2.$Proxy172.persist(Unknown Source)
2026-06-14 08:00:55     at org.springframework.data.jpa.repository.support.SimpleJpaRepository.save(SimpleJpaRepository.java:618)
2026-06-14 08:00:55     at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(Unknown Source)
2026-06-14 08:00:55     at java.base/java.lang.reflect.Method.invoke(Unknown Source)
2026-06-14 08:00:55     at org.springframework.aop.support.AopUtils.invokeJoinpointUsingReflection(AopUtils.java:354)
2026-06-14 08:00:55     at org.springframework.data.repository.core.support.RepositoryMethodInvoker$RepositoryFragmentMethodInvoker.lambda$new$0(RepositoryMethodInvoker.java:277)
2026-06-14 08:00:55     at org.springframework.data.repository.core.support.RepositoryMethodInvoker.doInvoke(RepositoryMethodInvoker.java:170)
2026-06-14 08:00:55     at org.springframework.data.repository.core.support.RepositoryMethodInvoker.invoke(RepositoryMethodInvoker.java:158)
2026-06-14 08:00:55     at org.springframework.data.repository.core.support.RepositoryComposition$RepositoryFragments.invoke(RepositoryComposition.java:516)
2026-06-14 08:00:55     at org.springframework.data.repository.core.support.RepositoryComposition.invoke(RepositoryComposition.java:285)
2026-06-14 08:00:55     at org.springframework.data.repository.core.support.RepositoryFactorySupport$ImplementationMethodExecutionInterceptor.invoke(RepositoryFactorySupport.java:628)
2026-06-14 08:00:55     at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-06-14 08:00:55     at org.springframework.data.repository.core.support.QueryExecutorMethodInterceptor.doInvoke(QueryExecutorMethodInterceptor.java:168)
2026-06-14 08:00:55     at org.springframework.data.repository.core.support.QueryExecutorMethodInterceptor.invoke(QueryExecutorMethodInterceptor.java:143)
2026-06-14 08:00:55     at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-06-14 08:00:55     at org.springframework.data.projection.DefaultMethodInvokingMethodInterceptor.invoke(DefaultMethodInvokingMethodInterceptor.java:70)
2026-06-14 08:00:55     at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-06-14 08:00:55     at org.springframework.transaction.interceptor.TransactionInterceptor$1.proceedWithInvocation(TransactionInterceptor.java:123)
2026-06-14 08:00:55     at org.springframework.transaction.interceptor.TransactionAspectSupport.invokeWithinTransaction(TransactionAspectSupport.java:392)
2026-06-14 08:00:55     at org.springframework.transaction.interceptor.TransactionInterceptor.invoke(TransactionInterceptor.java:119)
2026-06-14 08:00:55     at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-06-14 08:00:55     at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:137)
2026-06-14 08:00:55     ... 19 common frames omitted
2026-06-14 08:00:55 Caused by: org.postgresql.util.PSQLException: ERROR: column "care_plan" is of type jsonb but expression is of type character varying
2026-06-14 08:00:55   Hint: You will need to rewrite or cast the expression.
2026-06-14 08:00:55   Position: 186
2026-06-14 08:00:55     at org.postgresql.core.v3.QueryExecutorImpl.receiveErrorResponse(QueryExecutorImpl.java:2713)
2026-06-14 08:00:55     at org.postgresql.core.v3.QueryExecutorImpl.processResults(QueryExecutorImpl.java:2401)
2026-06-14 08:00:55     at org.postgresql.core.v3.QueryExecutorImpl.execute(QueryExecutorImpl.java:368)
2026-06-14 08:00:55     at org.postgresql.jdbc.PgStatement.executeInternal(PgStatement.java:498)
2026-06-14 08:00:55     at org.postgresql.jdbc.PgStatement.execute(PgStatement.java:415)
2026-06-14 08:00:55     at org.postgresql.jdbc.PgPreparedStatement.executeWithFlags(PgPreparedStatement.java:190)
2026-06-14 08:00:55     at org.postgresql.jdbc.PgPreparedStatement.executeUpdate(PgPreparedStatement.java:152)
2026-06-14 08:00:55     at com.zaxxer.hikari.pool.ProxyPreparedStatement.executeUpdate(ProxyPreparedStatement.java:61)
2026-06-14 08:00:55     at com.zaxxer.hikari.pool.HikariProxyPreparedStatement.executeUpdate(HikariProxyPreparedStatement.java)
2026-06-14 08:00:55     at org.hibernate.engine.jdbc.internal.ResultSetReturnImpl.executeUpdate(ResultSetReturnImpl.java:194)
2026-06-14 08:00:55     ... 65 common frames omitted
2026-06-14 08:00:55 2026-06-14 07:00:55 [ai-task-1] [] WARN  o.h.e.jdbc.spi.SqlExceptionHelper - SQL Error: 0, SQLState: 42804
2026-06-14 08:00:55 2026-06-14 07:00:55 [ai-task-1] [] ERROR o.h.e.jdbc.spi.SqlExceptionHelper - ERROR: column "care_plan" is of type jsonb but expression is of type character varying
2026-06-14 08:00:55   Hint: You will need to rewrite or cast the expression.
2026-06-14 08:00:55   Position: 186
2026-06-14 08:00:55 2026-06-14 07:00:55 [ai-task-1] [] ERROR c.p.i.s.i.IdentificationServiceImpl - Failed to mark identification as FAILED: id=null
2026-06-14 08:00:55 org.springframework.dao.InvalidDataAccessResourceUsageException: could not execute statement [ERROR: column "care_plan" is of type jsonb but expression is of type character varying
2026-06-14 08:00:55   Hint: You will need to rewrite or cast the expression.
2026-06-14 08:00:55   Position: 186] [insert into public.identifications (care_plan,common_name,confidence,created_at,created_by,photo_url,plant_id,raw_response,scientific_name,status,updated_at,updated_by,user_id) values (?,?,?,?,?,?,?,?,?,?,?,?,?)]; SQL [insert into public.identifications (care_plan,common_name,confidence,created_at,created_by,photo_url,plant_id,raw_response,scientific_name,status,updated_at,updated_by,user_id) values (?,?,?,?,?,?,?,?,?,?,?,?,?)]
2026-06-14 08:00:55     at org.springframework.orm.jpa.vendor.HibernateJpaDialect.convertHibernateAccessException(HibernateJpaDialect.java:277)
2026-06-14 08:00:55     at org.springframework.orm.jpa.vendor.HibernateJpaDialect.translateExceptionIfPossible(HibernateJpaDialect.java:241)
2026-06-14 08:00:55     at org.springframework.orm.jpa.AbstractEntityManagerFactoryBean.translateExceptionIfPossible(AbstractEntityManagerFactoryBean.java:550)
2026-06-14 08:00:55     at org.springframework.dao.support.ChainedPersistenceExceptionTranslator.translateExceptionIfPossible(ChainedPersistenceExceptionTranslator.java:61)
2026-06-14 08:00:55     at org.springframework.dao.support.DataAccessUtils.translateIfNecessary(DataAccessUtils.java:335)
2026-06-14 08:00:55     at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:152)
2026-06-14 08:00:55     at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-06-14 08:00:55     at org.springframework.data.jpa.repository.support.CrudMethodMetadataPostProcessor$CrudMethodMetadataPopulatingMethodInterceptor.invoke(CrudMethodMetadataPostProcessor.java:164)
2026-06-14 08:00:55     at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-06-14 08:00:55     at org.springframework.aop.interceptor.ExposeInvocationInterceptor.invoke(ExposeInvocationInterceptor.java:97)
2026-06-14 08:00:55     at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-06-14 08:00:55     at org.springframework.aop.framework.JdkDynamicAopProxy.invoke(JdkDynamicAopProxy.java:223)
2026-06-14 08:00:55     at jdk.proxy2/jdk.proxy2.$Proxy190.save(Unknown Source)
2026-06-14 08:00:55     at com.plantpal.identification.service.impl.IdentificationServiceImpl.markFailed(IdentificationServiceImpl.java:314)
2026-06-14 08:00:55     at com.plantpal.identification.service.impl.IdentificationServiceImpl.identify(IdentificationServiceImpl.java:151)
2026-06-14 08:00:55     at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(Unknown Source)
2026-06-14 08:00:55     at java.base/java.lang.reflect.Method.invoke(Unknown Source)
2026-06-14 08:00:55     at org.springframework.aop.support.AopUtils.invokeJoinpointUsingReflection(AopUtils.java:354)
2026-06-14 08:00:55     at org.springframework.aop.framework.ReflectiveMethodInvocation.invokeJoinpoint(ReflectiveMethodInvocation.java:196)
2026-06-14 08:00:55     at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:163)
2026-06-14 08:00:55     at org.springframework.aop.interceptor.AsyncExecutionInterceptor.lambda$invoke$0(AsyncExecutionInterceptor.java:113)
2026-06-14 08:00:55     at org.springframework.util.concurrent.FutureUtils.lambda$toSupplier$0(FutureUtils.java:74)
2026-06-14 08:00:55     at java.base/java.util.concurrent.CompletableFuture$AsyncSupply.run(Unknown Source)
2026-06-14 08:00:55     at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(Unknown Source)
2026-06-14 08:00:55     at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(Unknown Source)
2026-06-14 08:00:55     at java.base/java.lang.Thread.run(Unknown Source)
2026-06-14 08:00:55 Caused by: org.hibernate.exception.SQLGrammarException: could not execute statement [ERROR: column "care_plan" is of type jsonb but expression is of type character varying
2026-06-14 08:00:55   Hint: You will need to rewrite or cast the expression.
2026-06-14 08:00:55   Position: 186] [insert into public.identifications (care_plan,common_name,confidence,created_at,created_by,photo_url,plant_id,raw_response,scientific_name,status,updated_at,updated_by,user_id) values (?,?,?,?,?,?,?,?,?,?,?,?,?)]
2026-06-14 08:00:55     at org.hibernate.exception.internal.SQLStateConversionDelegate.convert(SQLStateConversionDelegate.java:91)
2026-06-14 08:00:55     at org.hibernate.exception.internal.StandardSQLExceptionConverter.convert(StandardSQLExceptionConverter.java:58)
2026-06-14 08:00:55     at org.hibernate.engine.jdbc.spi.SqlExceptionHelper.convert(SqlExceptionHelper.java:108)
2026-06-14 08:00:55     at org.hibernate.engine.jdbc.internal.ResultSetReturnImpl.executeUpdate(ResultSetReturnImpl.java:197)
2026-06-14 08:00:55     at org.hibernate.id.insert.GetGeneratedKeysDelegate.performInsert(GetGeneratedKeysDelegate.java:107)
2026-06-14 08:00:55     at org.hibernate.engine.jdbc.mutation.internal.MutationExecutorPostInsertSingleTable.execute(MutationExecutorPostInsertSingleTable.java:100)
2026-06-14 08:00:55     at org.hibernate.persister.entity.mutation.InsertCoordinator.doStaticInserts(InsertCoordinator.java:175)
2026-06-14 08:00:55     at org.hibernate.persister.entity.mutation.InsertCoordinator.coordinateInsert(InsertCoordinator.java:113)
2026-06-14 08:00:55     at org.hibernate.persister.entity.AbstractEntityPersister.insert(AbstractEntityPersister.java:2868)
2026-06-14 08:00:55     at org.hibernate.action.internal.EntityIdentityInsertAction.execute(EntityIdentityInsertAction.java:81)
2026-06-14 08:00:55     at org.hibernate.engine.spi.ActionQueue.execute(ActionQueue.java:670)
2026-06-14 08:00:55     at org.hibernate.engine.spi.ActionQueue.addResolvedEntityInsertAction(ActionQueue.java:291)
2026-06-14 08:00:55     at org.hibernate.engine.spi.ActionQueue.addInsertAction(ActionQueue.java:272)
2026-06-14 08:00:55     at org.hibernate.engine.spi.ActionQueue.addAction(ActionQueue.java:322)
2026-06-14 08:00:55     at org.hibernate.event.internal.AbstractSaveEventListener.addInsertAction(AbstractSaveEventListener.java:386)
2026-06-14 08:00:55     at org.hibernate.event.internal.AbstractSaveEventListener.performSaveOrReplicate(AbstractSaveEventListener.java:300)
2026-06-14 08:00:55     at org.hibernate.event.internal.AbstractSaveEventListener.performSave(AbstractSaveEventListener.java:219)
2026-06-14 08:00:55     at org.hibernate.event.internal.AbstractSaveEventListener.saveWithGeneratedId(AbstractSaveEventListener.java:134)
2026-06-14 08:00:55     at org.hibernate.event.internal.DefaultPersistEventListener.entityIsTransient(DefaultPersistEventListener.java:175)
2026-06-14 08:00:55     at org.hibernate.event.internal.DefaultPersistEventListener.persist(DefaultPersistEventListener.java:93)
2026-06-14 08:00:55     at org.hibernate.event.internal.DefaultPersistEventListener.onPersist(DefaultPersistEventListener.java:77)
2026-06-14 08:00:55     at org.hibernate.event.internal.DefaultPersistEventListener.onPersist(DefaultPersistEventListener.java:54)
2026-06-14 08:00:55     at org.hibernate.event.service.internal.EventListenerGroupImpl.fireEventOnEachListener(EventListenerGroupImpl.java:127)
2026-06-14 08:00:55     at org.hibernate.internal.SessionImpl.firePersist(SessionImpl.java:754)
2026-06-14 08:00:55     at org.hibernate.internal.SessionImpl.persist(SessionImpl.java:738)
2026-06-14 08:00:55     at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(Unknown Source)
2026-06-14 08:00:55     at java.base/java.lang.reflect.Method.invoke(Unknown Source)
2026-06-14 08:00:55     at org.springframework.orm.jpa.SharedEntityManagerCreator$SharedEntityManagerInvocationHandler.invoke(SharedEntityManagerCreator.java:319)
2026-06-14 08:00:55     at jdk.proxy2/jdk.proxy2.$Proxy172.persist(Unknown Source)
2026-06-14 08:00:55     at org.springframework.data.jpa.repository.support.SimpleJpaRepository.save(SimpleJpaRepository.java:618)
2026-06-14 08:00:55     at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(Unknown Source)
2026-06-14 08:00:55     at java.base/java.lang.reflect.Method.invoke(Unknown Source)
2026-06-14 08:00:55     at org.springframework.aop.support.AopUtils.invokeJoinpointUsingReflection(AopUtils.java:354)
2026-06-14 08:00:55     at org.springframework.data.repository.core.support.RepositoryMethodInvoker$RepositoryFragmentMethodInvoker.lambda$new$0(RepositoryMethodInvoker.java:277)
2026-06-14 08:00:55     at org.springframework.data.repository.core.support.RepositoryMethodInvoker.doInvoke(RepositoryMethodInvoker.java:170)
2026-06-14 08:00:55     at org.springframework.data.repository.core.support.RepositoryMethodInvoker.invoke(RepositoryMethodInvoker.java:158)
2026-06-14 08:00:55     at org.springframework.data.repository.core.support.RepositoryComposition$RepositoryFragments.invoke(RepositoryComposition.java:516)
2026-06-14 08:00:55     at org.springframework.data.repository.core.support.RepositoryComposition.invoke(RepositoryComposition.java:285)
2026-06-14 08:00:55     at org.springframework.data.repository.core.support.RepositoryFactorySupport$ImplementationMethodExecutionInterceptor.invoke(RepositoryFactorySupport.java:628)
2026-06-14 08:00:55     at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-06-14 08:00:55     at org.springframework.data.repository.core.support.QueryExecutorMethodInterceptor.doInvoke(QueryExecutorMethodInterceptor.java:168)
2026-06-14 08:00:55     at org.springframework.data.repository.core.support.QueryExecutorMethodInterceptor.invoke(QueryExecutorMethodInterceptor.java:143)
2026-06-14 08:00:55     at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-06-14 08:00:55     at org.springframework.data.projection.DefaultMethodInvokingMethodInterceptor.invoke(DefaultMethodInvokingMethodInterceptor.java:70)
2026-06-14 08:00:55     at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-06-14 08:00:55     at org.springframework.transaction.interceptor.TransactionInterceptor$1.proceedWithInvocation(TransactionInterceptor.java:123)
2026-06-14 08:00:55     at org.springframework.transaction.interceptor.TransactionAspectSupport.invokeWithinTransaction(TransactionAspectSupport.java:392)
2026-06-14 08:00:55     at org.springframework.transaction.interceptor.TransactionInterceptor.invoke(TransactionInterceptor.java:119)
2026-06-14 08:00:55     at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-06-14 08:00:55     at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:137)
2026-06-14 08:00:55     ... 20 common frames omitted
2026-06-14 08:00:55 Caused by: org.postgresql.util.PSQLException: ERROR: column "care_plan" is of type jsonb but expression is of type character varying
2026-06-14 08:00:55   Hint: You will need to rewrite or cast the expression.
2026-06-14 08:00:55   Position: 186
2026-06-14 08:00:55     at org.postgresql.core.v3.QueryExecutorImpl.receiveErrorResponse(QueryExecutorImpl.java:2713)
2026-06-14 08:00:55     at org.postgresql.core.v3.QueryExecutorImpl.processResults(QueryExecutorImpl.java:2401)
2026-06-14 08:00:55     at org.postgresql.core.v3.QueryExecutorImpl.execute(QueryExecutorImpl.java:368)
2026-06-14 08:00:55     at org.postgresql.jdbc.PgStatement.executeInternal(PgStatement.java:498)
2026-06-14 08:00:55     at org.postgresql.jdbc.PgStatement.execute(PgStatement.java:415)
2026-06-14 08:00:55     at org.postgresql.jdbc.PgPreparedStatement.executeWithFlags(PgPreparedStatement.java:190)
2026-06-14 08:00:55     at org.postgresql.jdbc.PgPreparedStatement.executeUpdate(PgPreparedStatement.java:152)
2026-06-14 08:00:55     at com.zaxxer.hikari.pool.ProxyPreparedStatement.executeUpdate(ProxyPreparedStatement.java:61)
2026-06-14 08:00:55     at com.zaxxer.hikari.pool.HikariProxyPreparedStatement.executeUpdate(HikariProxyPreparedStatement.java)
2026-06-14 08:00:55     at org.hibernate.engine.jdbc.internal.ResultSetReturnImpl.executeUpdate(ResultSetReturnImpl.java:194)
2026-06-14 08:00:55     ... 66 common frames omitted
2026-06-14 08:00:55 2026-06-14 07:00:55 [http-nio-8080-exec-6] [5f903ed5-cf46-4d70-b92d-fc1c9059eee2] DEBUG o.s.w.s.m.m.a.ExceptionHandlerExceptionResolver - Using @ExceptionHandler com.plantpal.shared.exception.GlobalExceptionHandler#handlePlantPal(PlantPalException)
2026-06-14 08:00:55 2026-06-14 07:00:55 [http-nio-8080-exec-6] [5f903ed5-cf46-4d70-b92d-fc1c9059eee2] ERROR c.p.s.e.GlobalExceptionHandler - Business error [code=500]: Identification failed: could not execute statement [ERROR: column "care_plan" is of type jsonb but expression is of type character varying
2026-06-14 08:00:55   Hint: You will need to rewrite or cast the expression.
2026-06-14 08:00:55   Position: 186] [insert into public.identifications (care_plan,common_name,confidence,created_at,created_by,photo_url,plant_id,raw_response,scientific_name,status,updated_at,updated_by,user_id) values (?,?,?,?,?,?,?,?,?,?,?,?,?)]; SQL [insert into public.identifications (care_plan,common_name,confidence,created_at,created_by,photo_url,plant_id,raw_response,scientific_name,status,updated_at,updated_by,user_id) values (?,?,?,?,?,?,?,?,?,?,?,?,?)]
2026-06-14 08:00:55 com.plantpal.shared.exception.PlantPalException: Identification failed: could not execute statement [ERROR: column "care_plan" is of type jsonb but expression is of type character varying
2026-06-14 08:00:55   Hint: You will need to rewrite or cast the expression.
2026-06-14 08:00:55   Position: 186] [insert into public.identifications (care_plan,common_name,confidence,created_at,created_by,photo_url,plant_id,raw_response,scientific_name,status,updated_at,updated_by,user_id) values (?,?,?,?,?,?,?,?,?,?,?,?,?)]; SQL [insert into public.identifications (care_plan,common_name,confidence,created_at,created_by,photo_url,plant_id,raw_response,scientific_name,status,updated_at,updated_by,user_id) values (?,?,?,?,?,?,?,?,?,?,?,?,?)]
2026-06-14 08:00:55     at com.plantpal.identification.service.impl.IdentificationServiceImpl.identify(IdentificationServiceImpl.java:152)
2026-06-14 08:00:55     at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(Unknown Source)
2026-06-14 08:00:55     at java.base/java.lang.reflect.Method.invoke(Unknown Source)
2026-06-14 08:00:55     at org.springframework.aop.support.AopUtils.invokeJoinpointUsingReflection(AopUtils.java:354)
2026-06-14 08:00:55     at org.springframework.aop.framework.ReflectiveMethodInvocation.invokeJoinpoint(ReflectiveMethodInvocation.java:196)
2026-06-14 08:00:55     at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:163)
2026-06-14 08:00:55     at org.springframework.aop.interceptor.AsyncExecutionInterceptor.lambda$invoke$0(AsyncExecutionInterceptor.java:113)
2026-06-14 08:00:55     at org.springframework.util.concurrent.FutureUtils.lambda$toSupplier$0(FutureUtils.java:74)
2026-06-14 08:00:55     at java.base/java.util.concurrent.CompletableFuture$AsyncSupply.run(Unknown Source)
2026-06-14 08:00:55     at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(Unknown Source)
2026-06-14 08:00:55     at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(Unknown Source)
2026-06-14 08:00:55     at java.base/java.lang.Thread.run(Unknown Source)
2026-06-14 08:00:55 2026-06-14 07:00:55 [http-nio-8080-exec-6] [5f903ed5-cf46-4d70-b92d-fc1c9059eee2] DEBUG o.s.w.s.m.m.a.HttpEntityMethodProcessor - Using 'application/json', given [application/json, text/plain, */*] and supported [application/json, application/*+json]
2026-06-14 08:00:55 2026-06-14 07:00:55 [http-nio-8080-exec-6] [5f903ed5-cf46-4d70-b92d-fc1c9059eee2] DEBUG o.s.w.s.m.m.a.HttpEntityMethodProcessor - Writing [com.plantpal.shared.dto.ApiResponse@52b04b37]
2026-06-14 08:00:55 2026-06-14 07:00:55 [http-nio-8080-exec-6] [5f903ed5-cf46-4d70-b92d-fc1c9059eee2] DEBUG o.s.w.s.m.m.a.ExceptionHandlerExceptionResolver - Resolved [com.plantpal.shared.exception.PlantPalException: Identification failed: could not execute statement [ERROR: column "care_plan" is of type jsonb but expression is of type character varying<EOL>  Hint: You will need to rewrite or cast the expression.<EOL>  Position: 186] [insert into public.identifications (care_plan,common_name,confidence,created_at,created_by,photo_url,plant_id,raw_response,scientific_name,status,updated_at,updated_by,user_id) values (?,?,?,?,?,?,?,?,?,?,?,?,?)]; SQL [insert into public.identifications (care_plan,common_name,confidence,created_at,created_by,photo_url,plant_id,raw_response,scientific_name,status,updated_at,updated_by,user_id) values (?,?,?,?,?,?,?,?,?,?,?,?,?)]]
2026-06-14 08:00:55 2026-06-14 07:00:55 [http-nio-8080-exec-6] [5f903ed5-cf46-4d70-b92d-fc1c9059eee2] DEBUG o.s.web.servlet.DispatcherServlet - Completed 500 INTERNAL_SERVER_ERROR
2026-06-14 08:01:13 2026-06-14 07:01:13 [http-nio-8080-exec-9] [b4757577-5200-4a97-b068-9a1b57b6b987] DEBUG o.s.security.web.FilterChainProxy - Securing GET /actuator/health
2026-06-14 08:01:13 2026-06-14 07:01:13 [http-nio-8080-exec-9] [b4757577-5200-4a97-b068-9a1b57b6b987] DEBUG o.s.s.w.a.AnonymousAuthenticationFilter - Set SecurityContextHolder to anonymous SecurityContext
2026-06-14 08:01:13 2026-06-14 07:01:13 [http-nio-8080-exec-9] [b4757577-5200-4a97-b068-9a1b57b6b987] DEBUG o.s.security.web.FilterChainProxy - Secured GET /actuator/health
2026-06-14 08:01:13 2026-06-14 07:01:13 [http-nio-8080-exec-9] [b4757577-5200-4a97-b068-9a1b57b6b987] DEBUG o.s.web.servlet.DispatcherServlet - GET "/actuator/health", parameters={}
2026-06-14 08:01:13 2026-06-14 07:01:13 [http-nio-8080-exec-9] [b4757577-5200-4a97-b068-9a1b57b6b987] DEBUG o.s.w.s.m.m.a.RequestResponseBodyMethodProcessor - Read "application/octet-stream" to []
2026-06-14 08:01:13 2026-06-14 07:01:13 [http-nio-8080-exec-9] [b4757577-5200-4a97-b068-9a1b57b6b987] DEBUG o.s.w.s.m.m.a.HttpEntityMethodProcessor - Using 'application/vnd.spring-boot.actuator.v3+json', given [*/*] and supported [application/vnd.spring-boot.actuator.v3+json, application/vnd.spring-boot.actuator.v2+json, application/json]
2026-06-14 08:01:13 2026-06-14 07:01:13 [http-nio-8080-exec-9] [b4757577-5200-4a97-b068-9a1b57b6b987] DEBUG o.s.w.s.m.m.a.HttpEntityMethodProcessor - Writing [org.springframework.boot.actuate.health.SystemHealth@2753f17c]
2026-06-14 08:01:13 2026-06-14 07:01:13 [http-nio-8080-exec-9] [b4757577-5200-4a97-b068-9a1b57b6b987] DEBUG o.s.web.servlet.DispatcherServlet - Completed 200 OK
2026-06-14 08:01:30 2026-06-14 07:01:30 [http-nio-8080-exec-5] [726d7166-bbcc-459d-8071-5230fe2c2d85] DEBUG o.s.security.web.FilterChainProxy - Securing GET /api/v1/plants?page=0&size=20&sort=createdAt,desc
2026-06-14 08:01:30 2026-06-14 07:01:30 [http-nio-8080-exec-5] [726d7166-bbcc-459d-8071-5230fe2c2d85] DEBUG o.s.security.web.FilterChainProxy - Secured GET /api/v1/plants?page=0&size=20&sort=createdAt,desc
2026-06-14 08:01:30 2026-06-14 07:01:30 [http-nio-8080-exec-5] [726d7166-bbcc-459d-8071-5230fe2c2d85] DEBUG o.s.web.servlet.DispatcherServlet - GET "/api/v1/plants?page=0&size=20&sort=createdAt,desc", parameters={masked}
2026-06-14 08:01:30 2026-06-14 07:01:30 [http-nio-8080-exec-5] [726d7166-bbcc-459d-8071-5230fe2c2d85] DEBUG o.s.w.s.m.m.a.RequestMappingHandlerMapping - Mapped to com.plantpal.plant.controller.PlantController#getUserPlants(Pageable)
2026-06-14 08:01:30 2026-06-14 07:01:30 [http-nio-8080-exec-5] [726d7166-bbcc-459d-8071-5230fe2c2d85] DEBUG o.s.w.s.m.m.a.HttpEntityMethodProcessor - Using 'application/json', given [application/json, text/plain, */*] and supported [application/json, application/*+json]
2026-06-14 08:01:30 2026-06-14 07:01:30 [http-nio-8080-exec-5] [726d7166-bbcc-459d-8071-5230fe2c2d85] DEBUG o.s.w.s.m.m.a.HttpEntityMethodProcessor - Writing [com.plantpal.shared.dto.ApiResponse@253891c]
2026-06-14 08:01:30 2026-06-14 07:01:30 [http-nio-8080-exec-5] [726d7166-bbcc-459d-8071-5230fe2c2d85] DEBUG o.s.web.servlet.DispatcherServlet - Completed 200 OK
2026-06-14 08:01:43 2026-06-14 07:01:43 [http-nio-8080-exec-8] [cf93c765-3b80-4c7e-ab84-e6da300b25f7] DEBUG o.s.security.web.FilterChainProxy - Securing GET /actuator/health
2026-06-14 08:01:43 2026-06-14 07:01:43 [http-nio-8080-exec-8] [cf93c765-3b80-4c7e-ab84-e6da300b25f7] DEBUG o.s.s.w.a.AnonymousAuthenticationFilter - Set SecurityContextHolder to anonymous SecurityContext
2026-06-14 08:01:43 2026-06-14 07:01:43 [http-nio-8080-exec-8] [cf93c765-3b80-4c7e-ab84-e6da300b25f7] DEBUG o.s.security.web.FilterChainProxy - Secured GET /actuator/health
2026-06-14 08:01:43 2026-06-14 07:01:43 [http-nio-8080-exec-8] [cf93c765-3b80-4c7e-ab84-e6da300b25f7] DEBUG o.s.web.servlet.DispatcherServlet - GET "/actuator/health", parameters={}
2026-06-14 08:01:43 2026-06-14 07:01:43 [http-nio-8080-exec-8] [cf93c765-3b80-4c7e-ab84-e6da300b25f7] DEBUG o.s.w.s.m.m.a.RequestResponseBodyMethodProcessor - Read "application/octet-stream" to []
2026-06-14 08:01:43 2026-06-14 07:01:43 [http-nio-8080-exec-8] [cf93c765-3b80-4c7e-ab84-e6da300b25f7] DEBUG o.s.w.s.m.m.a.HttpEntityMethodProcessor - Using 'application/vnd.spring-boot.actuator.v3+json', given [*/*] and supported [application/vnd.spring-boot.actuator.v3+json, application/vnd.spring-boot.actuator.v2+json, application/json]
2026-06-14 08:01:43 2026-06-14 07:01:43 [http-nio-8080-exec-8] [cf93c765-3b80-4c7e-ab84-e6da300b25f7] DEBUG o.s.w.s.m.m.a.HttpEntityMethodProcessor - Writing [org.springframework.boot.actuate.health.SystemHealth@6c792e3b]
2026-06-14 08:01:43 2026-06-14 07:01:43 [http-nio-8080-exec-8] [cf93c765-3b80-4c7e-ab84-e6da300b25f7] DEBUG o.s.web.servlet.DispatcherServlet - Completed 200 OK
2026-06-14 08:01:46 2026-06-14 07:01:46 [http-nio-8080-exec-7] [c04ed82b-341b-4742-8a41-0d8b9a1df8da] DEBUG o.s.security.web.FilterChainProxy - Securing POST /api/v1/identifications/analyze
2026-06-14 08:01:46 2026-06-14 07:01:46 [http-nio-8080-exec-7] [c04ed82b-341b-4742-8a41-0d8b9a1df8da] DEBUG o.s.security.web.FilterChainProxy - Secured POST /api/v1/identifications/analyze
2026-06-14 08:01:46 2026-06-14 07:01:46 [http-nio-8080-exec-7] [c04ed82b-341b-4742-8a41-0d8b9a1df8da] DEBUG o.s.web.servlet.DispatcherServlet - POST "/api/v1/identifications/analyze", parameters={multipart}
2026-06-14 08:01:46 2026-06-14 07:01:46 [http-nio-8080-exec-7] [c04ed82b-341b-4742-8a41-0d8b9a1df8da] DEBUG o.s.w.s.m.m.a.RequestMappingHandlerMapping - Mapped to com.plantpal.identification.controller.IdentificationController#analyze(List, List, Long)
2026-06-14 08:01:46 2026-06-14 07:01:46 [http-nio-8080-exec-7] [c04ed82b-341b-4742-8a41-0d8b9a1df8da] INFO  c.p.i.c.IdentificationController - Identification requested: userId=1, images=1, plantId=null
2026-06-14 08:01:46 2026-06-14 07:01:46 [ai-task-2] [] DEBUG c.p.s.s.LocalFileStorageService - Saved photo: /tmp/plantpal/photos/724400ae-e9a7-48cb-a03c-5c840dd7cd62.jpg
2026-06-14 08:01:46 2026-06-14 07:01:46 [ai-task-2] [] WARN  o.h.e.jdbc.spi.SqlExceptionHelper - SQL Error: 0, SQLState: 42804
2026-06-14 08:01:46 2026-06-14 07:01:46 [ai-task-2] [] ERROR o.h.e.jdbc.spi.SqlExceptionHelper - ERROR: column "care_plan" is of type jsonb but expression is of type character varying
2026-06-14 08:01:46   Hint: You will need to rewrite or cast the expression.
2026-06-14 08:01:46   Position: 186
2026-06-14 08:01:46 2026-06-14 07:01:46 [ai-task-2] [] ERROR c.p.i.s.i.IdentificationServiceImpl - Identification failed for userId=1
2026-06-14 08:01:46 org.springframework.dao.InvalidDataAccessResourceUsageException: could not execute statement [ERROR: column "care_plan" is of type jsonb but expression is of type character varying
2026-06-14 08:01:46   Hint: You will need to rewrite or cast the expression.
2026-06-14 08:01:46   Position: 186] [insert into public.identifications (care_plan,common_name,confidence,created_at,created_by,photo_url,plant_id,raw_response,scientific_name,status,updated_at,updated_by,user_id) values (?,?,?,?,?,?,?,?,?,?,?,?,?)]; SQL [insert into public.identifications (care_plan,common_name,confidence,created_at,created_by,photo_url,plant_id,raw_response,scientific_name,status,updated_at,updated_by,user_id) values (?,?,?,?,?,?,?,?,?,?,?,?,?)]
2026-06-14 08:01:46     at org.springframework.orm.jpa.vendor.HibernateJpaDialect.convertHibernateAccessException(HibernateJpaDialect.java:277)
2026-06-14 08:01:46     at org.springframework.orm.jpa.vendor.HibernateJpaDialect.translateExceptionIfPossible(HibernateJpaDialect.java:241)
2026-06-14 08:01:46     at org.springframework.orm.jpa.AbstractEntityManagerFactoryBean.translateExceptionIfPossible(AbstractEntityManagerFactoryBean.java:550)
2026-06-14 08:01:46     at org.springframework.dao.support.ChainedPersistenceExceptionTranslator.translateExceptionIfPossible(ChainedPersistenceExceptionTranslator.java:61)
2026-06-14 08:01:46     at org.springframework.dao.support.DataAccessUtils.translateIfNecessary(DataAccessUtils.java:335)
2026-06-14 08:01:46     at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:152)
2026-06-14 08:01:46     at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-06-14 08:01:46     at org.springframework.data.jpa.repository.support.CrudMethodMetadataPostProcessor$CrudMethodMetadataPopulatingMethodInterceptor.invoke(CrudMethodMetadataPostProcessor.java:164)
2026-06-14 08:01:46     at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-06-14 08:01:46     at org.springframework.aop.interceptor.ExposeInvocationInterceptor.invoke(ExposeInvocationInterceptor.java:97)
2026-06-14 08:01:46     at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-06-14 08:01:46     at org.springframework.aop.framework.JdkDynamicAopProxy.invoke(JdkDynamicAopProxy.java:223)
2026-06-14 08:01:46     at jdk.proxy2/jdk.proxy2.$Proxy190.save(Unknown Source)
2026-06-14 08:01:46     at com.plantpal.identification.service.impl.IdentificationServiceImpl.identify(IdentificationServiceImpl.java:107)
2026-06-14 08:01:46     at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(Unknown Source)
2026-06-14 08:01:46     at java.base/java.lang.reflect.Method.invoke(Unknown Source)
2026-06-14 08:01:46     at org.springframework.aop.support.AopUtils.invokeJoinpointUsingReflection(AopUtils.java:354)
2026-06-14 08:01:46     at org.springframework.aop.framework.ReflectiveMethodInvocation.invokeJoinpoint(ReflectiveMethodInvocation.java:196)
2026-06-14 08:01:46     at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:163)
2026-06-14 08:01:46     at org.springframework.aop.interceptor.AsyncExecutionInterceptor.lambda$invoke$0(AsyncExecutionInterceptor.java:113)
2026-06-14 08:01:46     at org.springframework.util.concurrent.FutureUtils.lambda$toSupplier$0(FutureUtils.java:74)
2026-06-14 08:01:46     at java.base/java.util.concurrent.CompletableFuture$AsyncSupply.run(Unknown Source)
2026-06-14 08:01:46     at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(Unknown Source)
2026-06-14 08:01:46     at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(Unknown Source)
2026-06-14 08:01:46     at java.base/java.lang.Thread.run(Unknown Source)
2026-06-14 08:01:46 Caused by: org.hibernate.exception.SQLGrammarException: could not execute statement [ERROR: column "care_plan" is of type jsonb but expression is of type character varying
2026-06-14 08:01:46   Hint: You will need to rewrite or cast the expression.
2026-06-14 08:01:46   Position: 186] [insert into public.identifications (care_plan,common_name,confidence,created_at,created_by,photo_url,plant_id,raw_response,scientific_name,status,updated_at,updated_by,user_id) values (?,?,?,?,?,?,?,?,?,?,?,?,?)]
2026-06-14 08:01:46     at org.hibernate.exception.internal.SQLStateConversionDelegate.convert(SQLStateConversionDelegate.java:91)
2026-06-14 08:01:46     at org.hibernate.exception.internal.StandardSQLExceptionConverter.convert(StandardSQLExceptionConverter.java:58)
2026-06-14 08:01:46     at org.hibernate.engine.jdbc.spi.SqlExceptionHelper.convert(SqlExceptionHelper.java:108)
2026-06-14 08:01:46     at org.hibernate.engine.jdbc.internal.ResultSetReturnImpl.executeUpdate(ResultSetReturnImpl.java:197)
2026-06-14 08:01:46     at org.hibernate.id.insert.GetGeneratedKeysDelegate.performInsert(GetGeneratedKeysDelegate.java:107)
2026-06-14 08:01:46     at org.hibernate.engine.jdbc.mutation.internal.MutationExecutorPostInsertSingleTable.execute(MutationExecutorPostInsertSingleTable.java:100)
2026-06-14 08:01:46     at org.hibernate.persister.entity.mutation.InsertCoordinator.doStaticInserts(InsertCoordinator.java:175)
2026-06-14 08:01:46     at org.hibernate.persister.entity.mutation.InsertCoordinator.coordinateInsert(InsertCoordinator.java:113)
2026-06-14 08:01:46     at org.hibernate.persister.entity.AbstractEntityPersister.insert(AbstractEntityPersister.java:2868)
2026-06-14 08:01:46     at org.hibernate.action.internal.EntityIdentityInsertAction.execute(EntityIdentityInsertAction.java:81)
2026-06-14 08:01:46     at org.hibernate.engine.spi.ActionQueue.execute(ActionQueue.java:670)
2026-06-14 08:01:46     at org.hibernate.engine.spi.ActionQueue.addResolvedEntityInsertAction(ActionQueue.java:291)
2026-06-14 08:01:46     at org.hibernate.engine.spi.ActionQueue.addInsertAction(ActionQueue.java:272)
2026-06-14 08:01:46     at org.hibernate.engine.spi.ActionQueue.addAction(ActionQueue.java:322)
2026-06-14 08:01:46     at org.hibernate.event.internal.AbstractSaveEventListener.addInsertAction(AbstractSaveEventListener.java:386)
2026-06-14 08:01:46     at org.hibernate.event.internal.AbstractSaveEventListener.performSaveOrReplicate(AbstractSaveEventListener.java:300)
2026-06-14 08:01:46     at org.hibernate.event.internal.AbstractSaveEventListener.performSave(AbstractSaveEventListener.java:219)
2026-06-14 08:01:46     at org.hibernate.event.internal.AbstractSaveEventListener.saveWithGeneratedId(AbstractSaveEventListener.java:134)
2026-06-14 08:01:46     at org.hibernate.event.internal.DefaultPersistEventListener.entityIsTransient(DefaultPersistEventListener.java:175)
2026-06-14 08:01:46     at org.hibernate.event.internal.DefaultPersistEventListener.persist(DefaultPersistEventListener.java:93)
2026-06-14 08:01:46     at org.hibernate.event.internal.DefaultPersistEventListener.onPersist(DefaultPersistEventListener.java:77)
2026-06-14 08:01:46     at org.hibernate.event.internal.DefaultPersistEventListener.onPersist(DefaultPersistEventListener.java:54)
2026-06-14 08:01:46     at org.hibernate.event.service.internal.EventListenerGroupImpl.fireEventOnEachListener(EventListenerGroupImpl.java:127)
2026-06-14 08:01:46     at org.hibernate.internal.SessionImpl.firePersist(SessionImpl.java:754)
2026-06-14 08:01:46     at org.hibernate.internal.SessionImpl.persist(SessionImpl.java:738)
2026-06-14 08:01:46     at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(Unknown Source)
2026-06-14 08:01:46     at java.base/java.lang.reflect.Method.invoke(Unknown Source)
2026-06-14 08:01:46     at org.springframework.orm.jpa.SharedEntityManagerCreator$SharedEntityManagerInvocationHandler.invoke(SharedEntityManagerCreator.java:319)
2026-06-14 08:01:46     at jdk.proxy2/jdk.proxy2.$Proxy172.persist(Unknown Source)
2026-06-14 08:01:46     at org.springframework.data.jpa.repository.support.SimpleJpaRepository.save(SimpleJpaRepository.java:618)
2026-06-14 08:01:46     at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(Unknown Source)
2026-06-14 08:01:46     at java.base/java.lang.reflect.Method.invoke(Unknown Source)
2026-06-14 08:01:46     at org.springframework.aop.support.AopUtils.invokeJoinpointUsingReflection(AopUtils.java:354)
2026-06-14 08:01:46     at org.springframework.data.repository.core.support.RepositoryMethodInvoker$RepositoryFragmentMethodInvoker.lambda$new$0(RepositoryMethodInvoker.java:277)
2026-06-14 08:01:46     at org.springframework.data.repository.core.support.RepositoryMethodInvoker.doInvoke(RepositoryMethodInvoker.java:170)
2026-06-14 08:01:46     at org.springframework.data.repository.core.support.RepositoryMethodInvoker.invoke(RepositoryMethodInvoker.java:158)
2026-06-14 08:01:46     at org.springframework.data.repository.core.support.RepositoryComposition$RepositoryFragments.invoke(RepositoryComposition.java:516)
2026-06-14 08:01:46     at org.springframework.data.repository.core.support.RepositoryComposition.invoke(RepositoryComposition.java:285)
2026-06-14 08:01:46     at org.springframework.data.repository.core.support.RepositoryFactorySupport$ImplementationMethodExecutionInterceptor.invoke(RepositoryFactorySupport.java:628)
2026-06-14 08:01:46     at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-06-14 08:01:46     at org.springframework.data.repository.core.support.QueryExecutorMethodInterceptor.doInvoke(QueryExecutorMethodInterceptor.java:168)
2026-06-14 08:01:46     at org.springframework.data.repository.core.support.QueryExecutorMethodInterceptor.invoke(QueryExecutorMethodInterceptor.java:143)
2026-06-14 08:01:46     at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-06-14 08:01:46     at org.springframework.data.projection.DefaultMethodInvokingMethodInterceptor.invoke(DefaultMethodInvokingMethodInterceptor.java:70)
2026-06-14 08:01:46     at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-06-14 08:01:46     at org.springframework.transaction.interceptor.TransactionInterceptor$1.proceedWithInvocation(TransactionInterceptor.java:123)
2026-06-14 08:01:46     at org.springframework.transaction.interceptor.TransactionAspectSupport.invokeWithinTransaction(TransactionAspectSupport.java:392)
2026-06-14 08:01:46     at org.springframework.transaction.interceptor.TransactionInterceptor.invoke(TransactionInterceptor.java:119)
2026-06-14 08:01:46     at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-06-14 08:01:46     at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:137)
2026-06-14 08:01:46     ... 19 common frames omitted
2026-06-14 08:01:46 Caused by: org.postgresql.util.PSQLException: ERROR: column "care_plan" is of type jsonb but expression is of type character varying
2026-06-14 08:01:46   Hint: You will need to rewrite or cast the expression.
2026-06-14 08:01:46   Position: 186
2026-06-14 08:01:46     at org.postgresql.core.v3.QueryExecutorImpl.receiveErrorResponse(QueryExecutorImpl.java:2713)
2026-06-14 08:01:46     at org.postgresql.core.v3.QueryExecutorImpl.processResults(QueryExecutorImpl.java:2401)
2026-06-14 08:01:46     at org.postgresql.core.v3.QueryExecutorImpl.execute(QueryExecutorImpl.java:368)
2026-06-14 08:01:46     at org.postgresql.jdbc.PgStatement.executeInternal(PgStatement.java:498)
2026-06-14 08:01:46     at org.postgresql.jdbc.PgStatement.execute(PgStatement.java:415)
2026-06-14 08:01:46     at org.postgresql.jdbc.PgPreparedStatement.executeWithFlags(PgPreparedStatement.java:190)
2026-06-14 08:01:46     at org.postgresql.jdbc.PgPreparedStatement.executeUpdate(PgPreparedStatement.java:152)
2026-06-14 08:01:46     at com.zaxxer.hikari.pool.ProxyPreparedStatement.executeUpdate(ProxyPreparedStatement.java:61)
2026-06-14 08:01:46     at com.zaxxer.hikari.pool.HikariProxyPreparedStatement.executeUpdate(HikariProxyPreparedStatement.java)
2026-06-14 08:01:46     at org.hibernate.engine.jdbc.internal.ResultSetReturnImpl.executeUpdate(ResultSetReturnImpl.java:194)
2026-06-14 08:01:46     ... 65 common frames omitted
2026-06-14 08:01:46 2026-06-14 07:01:46 [ai-task-2] [] WARN  o.h.e.jdbc.spi.SqlExceptionHelper - SQL Error: 0, SQLState: 42804
2026-06-14 08:01:46 2026-06-14 07:01:46 [ai-task-2] [] ERROR o.h.e.jdbc.spi.SqlExceptionHelper - ERROR: column "care_plan" is of type jsonb but expression is of type character varying
2026-06-14 08:01:46   Hint: You will need to rewrite or cast the expression.
2026-06-14 08:01:46   Position: 186
2026-06-14 08:01:46 2026-06-14 07:01:46 [ai-task-2] [] ERROR c.p.i.s.i.IdentificationServiceImpl - Failed to mark identification as FAILED: id=null
2026-06-14 08:01:46 org.springframework.dao.InvalidDataAccessResourceUsageException: could not execute statement [ERROR: column "care_plan" is of type jsonb but expression is of type character varying
2026-06-14 08:01:46   Hint: You will need to rewrite or cast the expression.
2026-06-14 08:01:46   Position: 186] [insert into public.identifications (care_plan,common_name,confidence,created_at,created_by,photo_url,plant_id,raw_response,scientific_name,status,updated_at,updated_by,user_id) values (?,?,?,?,?,?,?,?,?,?,?,?,?)]; SQL [insert into public.identifications (care_plan,common_name,confidence,created_at,created_by,photo_url,plant_id,raw_response,scientific_name,status,updated_at,updated_by,user_id) values (?,?,?,?,?,?,?,?,?,?,?,?,?)]
2026-06-14 08:01:46     at org.springframework.orm.jpa.vendor.HibernateJpaDialect.convertHibernateAccessException(HibernateJpaDialect.java:277)
2026-06-14 08:01:46     at org.springframework.orm.jpa.vendor.HibernateJpaDialect.translateExceptionIfPossible(HibernateJpaDialect.java:241)
2026-06-14 08:01:46     at org.springframework.orm.jpa.AbstractEntityManagerFactoryBean.translateExceptionIfPossible(AbstractEntityManagerFactoryBean.java:550)
2026-06-14 08:01:46     at org.springframework.dao.support.ChainedPersistenceExceptionTranslator.translateExceptionIfPossible(ChainedPersistenceExceptionTranslator.java:61)
2026-06-14 08:01:46     at org.springframework.dao.support.DataAccessUtils.translateIfNecessary(DataAccessUtils.java:335)
2026-06-14 08:01:46     at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:152)
2026-06-14 08:01:46     at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-06-14 08:01:46     at org.springframework.data.jpa.repository.support.CrudMethodMetadataPostProcessor$CrudMethodMetadataPopulatingMethodInterceptor.invoke(CrudMethodMetadataPostProcessor.java:164)
2026-06-14 08:01:46     at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-06-14 08:01:46     at org.springframework.aop.interceptor.ExposeInvocationInterceptor.invoke(ExposeInvocationInterceptor.java:97)
2026-06-14 08:01:46     at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-06-14 08:01:46     at org.springframework.aop.framework.JdkDynamicAopProxy.invoke(JdkDynamicAopProxy.java:223)
2026-06-14 08:01:46     at jdk.proxy2/jdk.proxy2.$Proxy190.save(Unknown Source)
2026-06-14 08:01:46     at com.plantpal.identification.service.impl.IdentificationServiceImpl.markFailed(IdentificationServiceImpl.java:314)
2026-06-14 08:01:46     at com.plantpal.identification.service.impl.IdentificationServiceImpl.identify(IdentificationServiceImpl.java:151)
2026-06-14 08:01:46     at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(Unknown Source)
2026-06-14 08:01:46     at java.base/java.lang.reflect.Method.invoke(Unknown Source)
2026-06-14 08:01:46     at org.springframework.aop.support.AopUtils.invokeJoinpointUsingReflection(AopUtils.java:354)
2026-06-14 08:01:46     at org.springframework.aop.framework.ReflectiveMethodInvocation.invokeJoinpoint(ReflectiveMethodInvocation.java:196)
2026-06-14 08:01:46     at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:163)
2026-06-14 08:01:46     at org.springframework.aop.interceptor.AsyncExecutionInterceptor.lambda$invoke$0(AsyncExecutionInterceptor.java:113)
2026-06-14 08:01:46     at org.springframework.util.concurrent.FutureUtils.lambda$toSupplier$0(FutureUtils.java:74)
2026-06-14 08:01:46     at java.base/java.util.concurrent.CompletableFuture$AsyncSupply.run(Unknown Source)
2026-06-14 08:01:46     at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(Unknown Source)
2026-06-14 08:01:46     at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(Unknown Source)
2026-06-14 08:01:46     at java.base/java.lang.Thread.run(Unknown Source)
2026-06-14 08:01:46 Caused by: org.hibernate.exception.SQLGrammarException: could not execute statement [ERROR: column "care_plan" is of type jsonb but expression is of type character varying
2026-06-14 08:01:46   Hint: You will need to rewrite or cast the expression.
2026-06-14 08:01:46   Position: 186] [insert into public.identifications (care_plan,common_name,confidence,created_at,created_by,photo_url,plant_id,raw_response,scientific_name,status,updated_at,updated_by,user_id) values (?,?,?,?,?,?,?,?,?,?,?,?,?)]
2026-06-14 08:01:46     at org.hibernate.exception.internal.SQLStateConversionDelegate.convert(SQLStateConversionDelegate.java:91)
2026-06-14 08:01:46     at org.hibernate.exception.internal.StandardSQLExceptionConverter.convert(StandardSQLExceptionConverter.java:58)
2026-06-14 08:01:46     at org.hibernate.engine.jdbc.spi.SqlExceptionHelper.convert(SqlExceptionHelper.java:108)
2026-06-14 08:01:46     at org.hibernate.engine.jdbc.internal.ResultSetReturnImpl.executeUpdate(ResultSetReturnImpl.java:197)
2026-06-14 08:01:46     at org.hibernate.id.insert.GetGeneratedKeysDelegate.performInsert(GetGeneratedKeysDelegate.java:107)
2026-06-14 08:01:46     at org.hibernate.engine.jdbc.mutation.internal.MutationExecutorPostInsertSingleTable.execute(MutationExecutorPostInsertSingleTable.java:100)
2026-06-14 08:01:46     at org.hibernate.persister.entity.mutation.InsertCoordinator.doStaticInserts(InsertCoordinator.java:175)
2026-06-14 08:01:46     at org.hibernate.persister.entity.mutation.InsertCoordinator.coordinateInsert(InsertCoordinator.java:113)
2026-06-14 08:01:46     at org.hibernate.persister.entity.AbstractEntityPersister.insert(AbstractEntityPersister.java:2868)
2026-06-14 08:01:46     at org.hibernate.action.internal.EntityIdentityInsertAction.execute(EntityIdentityInsertAction.java:81)
2026-06-14 08:01:46     at org.hibernate.engine.spi.ActionQueue.execute(ActionQueue.java:670)
2026-06-14 08:01:46     at org.hibernate.engine.spi.ActionQueue.addResolvedEntityInsertAction(ActionQueue.java:291)
2026-06-14 08:01:46     at org.hibernate.engine.spi.ActionQueue.addInsertAction(ActionQueue.java:272)
2026-06-14 08:01:46     at org.hibernate.engine.spi.ActionQueue.addAction(ActionQueue.java:322)
2026-06-14 08:01:46     at org.hibernate.event.internal.AbstractSaveEventListener.addInsertAction(AbstractSaveEventListener.java:386)
2026-06-14 08:01:46     at org.hibernate.event.internal.AbstractSaveEventListener.performSaveOrReplicate(AbstractSaveEventListener.java:300)
2026-06-14 08:01:46     at org.hibernate.event.internal.AbstractSaveEventListener.performSave(AbstractSaveEventListener.java:219)
2026-06-14 08:01:46     at org.hibernate.event.internal.AbstractSaveEventListener.saveWithGeneratedId(AbstractSaveEventListener.java:134)
2026-06-14 08:01:46     at org.hibernate.event.internal.DefaultPersistEventListener.entityIsTransient(DefaultPersistEventListener.java:175)
2026-06-14 08:01:46     at org.hibernate.event.internal.DefaultPersistEventListener.persist(DefaultPersistEventListener.java:93)
2026-06-14 08:01:46     at org.hibernate.event.internal.DefaultPersistEventListener.onPersist(DefaultPersistEventListener.java:77)
2026-06-14 08:01:46     at org.hibernate.event.internal.DefaultPersistEventListener.onPersist(DefaultPersistEventListener.java:54)
2026-06-14 08:01:46     at org.hibernate.event.service.internal.EventListenerGroupImpl.fireEventOnEachListener(EventListenerGroupImpl.java:127)
2026-06-14 08:01:46     at org.hibernate.internal.SessionImpl.firePersist(SessionImpl.java:754)
2026-06-14 08:01:46     at org.hibernate.internal.SessionImpl.persist(SessionImpl.java:738)
2026-06-14 08:01:46     at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(Unknown Source)
2026-06-14 08:01:46     at java.base/java.lang.reflect.Method.invoke(Unknown Source)
2026-06-14 08:01:46     at org.springframework.orm.jpa.SharedEntityManagerCreator$SharedEntityManagerInvocationHandler.invoke(SharedEntityManagerCreator.java:319)
2026-06-14 08:01:46     at jdk.proxy2/jdk.proxy2.$Proxy172.persist(Unknown Source)
2026-06-14 08:01:46     at org.springframework.data.jpa.repository.support.SimpleJpaRepository.save(SimpleJpaRepository.java:618)
2026-06-14 08:01:46     at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(Unknown Source)
2026-06-14 08:01:46     at java.base/java.lang.reflect.Method.invoke(Unknown Source)
2026-06-14 08:01:46     at org.springframework.aop.support.AopUtils.invokeJoinpointUsingReflection(AopUtils.java:354)
2026-06-14 08:01:46     at org.springframework.data.repository.core.support.RepositoryMethodInvoker$RepositoryFragmentMethodInvoker.lambda$new$0(RepositoryMethodInvoker.java:277)
2026-06-14 08:01:46     at org.springframework.data.repository.core.support.RepositoryMethodInvoker.doInvoke(RepositoryMethodInvoker.java:170)
2026-06-14 08:01:46     at org.springframework.data.repository.core.support.RepositoryMethodInvoker.invoke(RepositoryMethodInvoker.java:158)
2026-06-14 08:01:46     at org.springframework.data.repository.core.support.RepositoryComposition$RepositoryFragments.invoke(RepositoryComposition.java:516)
2026-06-14 08:01:46     at org.springframework.data.repository.core.support.RepositoryComposition.invoke(RepositoryComposition.java:285)
2026-06-14 08:01:46     at org.springframework.data.repository.core.support.RepositoryFactorySupport$ImplementationMethodExecutionInterceptor.invoke(RepositoryFactorySupport.java:628)
2026-06-14 08:01:46     at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-06-14 08:01:46     at org.springframework.data.repository.core.support.QueryExecutorMethodInterceptor.doInvoke(QueryExecutorMethodInterceptor.java:168)
2026-06-14 08:01:46     at org.springframework.data.repository.core.support.QueryExecutorMethodInterceptor.invoke(QueryExecutorMethodInterceptor.java:143)
2026-06-14 08:01:46     at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-06-14 08:01:46     at org.springframework.data.projection.DefaultMethodInvokingMethodInterceptor.invoke(DefaultMethodInvokingMethodInterceptor.java:70)
2026-06-14 08:01:46     at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-06-14 08:01:46     at org.springframework.transaction.interceptor.TransactionInterceptor$1.proceedWithInvocation(TransactionInterceptor.java:123)
2026-06-14 08:01:46     at org.springframework.transaction.interceptor.TransactionAspectSupport.invokeWithinTransaction(TransactionAspectSupport.java:392)
2026-06-14 08:01:46     at org.springframework.transaction.interceptor.TransactionInterceptor.invoke(TransactionInterceptor.java:119)
2026-06-14 08:01:46     at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184)
2026-06-14 08:01:46     at org.springframework.dao.support.PersistenceExceptionTranslationInterceptor.invoke(PersistenceExceptionTranslationInterceptor.java:137)
2026-06-14 08:01:46     ... 20 common frames omitted
2026-06-14 08:01:46 Caused by: org.postgresql.util.PSQLException: ERROR: column "care_plan" is of type jsonb but expression is of type character varying
2026-06-14 08:01:46   Hint: You will need to rewrite or cast the expression.
2026-06-14 08:01:46   Position: 186
2026-06-14 08:01:46     at org.postgresql.core.v3.QueryExecutorImpl.receiveErrorResponse(QueryExecutorImpl.java:2713)
2026-06-14 08:01:46     at org.postgresql.core.v3.QueryExecutorImpl.processResults(QueryExecutorImpl.java:2401)
2026-06-14 08:01:46     at org.postgresql.core.v3.QueryExecutorImpl.execute(QueryExecutorImpl.java:368)
2026-06-14 08:01:46     at org.postgresql.jdbc.PgStatement.executeInternal(PgStatement.java:498)
2026-06-14 08:01:46     at org.postgresql.jdbc.PgStatement.execute(PgStatement.java:415)
2026-06-14 08:01:46     at org.postgresql.jdbc.PgPreparedStatement.executeWithFlags(PgPreparedStatement.java:190)
2026-06-14 08:01:46     at org.postgresql.jdbc.PgPreparedStatement.executeUpdate(PgPreparedStatement.java:152)
2026-06-14 08:01:46     at com.zaxxer.hikari.pool.ProxyPreparedStatement.executeUpdate(ProxyPreparedStatement.java:61)
2026-06-14 08:01:46     at com.zaxxer.hikari.pool.HikariProxyPreparedStatement.executeUpdate(HikariProxyPreparedStatement.java)
2026-06-14 08:01:46     at org.hibernate.engine.jdbc.internal.ResultSetReturnImpl.executeUpdate(ResultSetReturnImpl.java:194)
2026-06-14 08:01:46     ... 66 common frames omitted
2026-06-14 08:01:46 2026-06-14 07:01:46 [http-nio-8080-exec-7] [c04ed82b-341b-4742-8a41-0d8b9a1df8da] DEBUG o.s.w.s.m.m.a.ExceptionHandlerExceptionResolver - Using @ExceptionHandler com.plantpal.shared.exception.GlobalExceptionHandler#handlePlantPal(PlantPalException)
2026-06-14 08:01:46 2026-06-14 07:01:46 [http-nio-8080-exec-7] [c04ed82b-341b-4742-8a41-0d8b9a1df8da] ERROR c.p.s.e.GlobalExceptionHandler - Business error [code=500]: Identification failed: could not execute statement [ERROR: column "care_plan" is of type jsonb but expression is of type character varying
2026-06-14 08:01:46   Hint: You will need to rewrite or cast the expression.
2026-06-14 08:01:46   Position: 186] [insert into public.identifications (care_plan,common_name,confidence,created_at,created_by,photo_url,plant_id,raw_response,scientific_name,status,updated_at,updated_by,user_id) values (?,?,?,?,?,?,?,?,?,?,?,?,?)]; SQL [insert into public.identifications (care_plan,common_name,confidence,created_at,created_by,photo_url,plant_id,raw_response,scientific_name,status,updated_at,updated_by,user_id) values (?,?,?,?,?,?,?,?,?,?,?,?,?)]
2026-06-14 08:01:46 com.plantpal.shared.exception.PlantPalException: Identification failed: could not execute statement [ERROR: column "care_plan" is of type jsonb but expression is of type character varying
2026-06-14 08:01:46   Hint: You will need to rewrite or cast the expression.
2026-06-14 08:01:46   Position: 186] [insert into public.identifications (care_plan,common_name,confidence,created_at,created_by,photo_url,plant_id,raw_response,scientific_name,status,updated_at,updated_by,user_id) values (?,?,?,?,?,?,?,?,?,?,?,?,?)]; SQL [insert into public.identifications (care_plan,common_name,confidence,created_at,created_by,photo_url,plant_id,raw_response,scientific_name,status,updated_at,updated_by,user_id) values (?,?,?,?,?,?,?,?,?,?,?,?,?)]
2026-06-14 08:01:46     at com.plantpal.identification.service.impl.IdentificationServiceImpl.identify(IdentificationServiceImpl.java:152)
2026-06-14 08:01:46     at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(Unknown Source)
2026-06-14 08:01:46     at java.base/java.lang.reflect.Method.invoke(Unknown Source)
2026-06-14 08:01:46     at org.springframework.aop.support.AopUtils.invokeJoinpointUsingReflection(AopUtils.java:354)
2026-06-14 08:01:46     at org.springframework.aop.framework.ReflectiveMethodInvocation.invokeJoinpoint(ReflectiveMethodInvocation.java:196)
2026-06-14 08:01:46     at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:163)
2026-06-14 08:01:46     at org.springframework.aop.interceptor.AsyncExecutionInterceptor.lambda$invoke$0(AsyncExecutionInterceptor.java:113)
2026-06-14 08:01:46     at org.springframework.util.concurrent.FutureUtils.lambda$toSupplier$0(FutureUtils.java:74)
2026-06-14 08:01:46     at java.base/java.util.concurrent.CompletableFuture$AsyncSupply.run(Unknown Source)
2026-06-14 08:01:46     at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(Unknown Source)
2026-06-14 08:01:46     at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(Unknown Source)
2026-06-14 08:01:46     at java.base/java.lang.Thread.run(Unknown Source)
2026-06-14 08:01:46 2026-06-14 07:01:46 [http-nio-8080-exec-7] [c04ed82b-341b-4742-8a41-0d8b9a1df8da] DEBUG o.s.w.s.m.m.a.HttpEntityMethodProcessor - Using 'application/json', given [application/json, text/plain, */*] and supported [application/json, application/*+json]
2026-06-14 08:01:46 2026-06-14 07:01:46 [http-nio-8080-exec-7] [c04ed82b-341b-4742-8a41-0d8b9a1df8da] DEBUG o.s.w.s.m.m.a.HttpEntityMethodProcessor - Writing [com.plantpal.shared.dto.ApiResponse@7bbed4b2]
2026-06-14 08:01:46 2026-06-14 07:01:46 [http-nio-8080-exec-7] [c04ed82b-341b-4742-8a41-0d8b9a1df8da] DEBUG o.s.w.s.m.m.a.ExceptionHandlerExceptionResolver - Resolved [com.plantpal.shared.exception.PlantPalException: Identification failed: could not execute statement [ERROR: column "care_plan" is of type jsonb but expression is of type character varying<EOL>  Hint: You will need to rewrite or cast the expression.<EOL>  Position: 186] [insert into public.identifications (care_plan,common_name,confidence,created_at,created_by,photo_url,plant_id,raw_response,scientific_name,status,updated_at,updated_by,user_id) values (?,?,?,?,?,?,?,?,?,?,?,?,?)]; SQL [insert into public.identifications (care_plan,common_name,confidence,created_at,created_by,photo_url,plant_id,raw_response,scientific_name,status,updated_at,updated_by,user_id) values (?,?,?,?,?,?,?,?,?,?,?,?,?)]]
2026-06-14 08:01:46 2026-06-14 07:01:46 [http-nio-8080-exec-7] [c04ed82b-341b-4742-8a41-0d8b9a1df8da] DEBUG o.s.web.servlet.DispatcherServlet - Completed 500 INTERNAL_SERVER_ERROR