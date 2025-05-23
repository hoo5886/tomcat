/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.naming.factory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.sql.DataSource;


/**
 * <p>
 * Object factory for resource links for shared data sources.
 * </p>
 */
public class DataSourceLinkFactory extends ResourceLinkFactory {

    public static void setGlobalContext(Context newGlobalContext) {
        ResourceLinkFactory.setGlobalContext(newGlobalContext);
    }
    // ------------------------------------------------- ObjectFactory Methods


    @Override
    public Object getObjectInstance(Object obj, Name name, Context nameCtx, Hashtable<?,?> environment)
            throws NamingException {
        Object result = super.getObjectInstance(obj, name, nameCtx, environment);
        // Can we process this request?
        if (result != null) {
            Reference ref = (Reference) obj;
            RefAddr userAttr = ref.get("username");
            RefAddr passAttr = ref.get("password");
            if (userAttr != null && passAttr != null && userAttr.getContent() != null &&
                    passAttr.getContent() != null) {
                result = wrapDataSource(result, userAttr.getContent().toString(), passAttr.getContent().toString());
            }
        }
        return result;
    }

    protected Object wrapDataSource(Object datasource, String username, String password) throws NamingException {
        try {
            DataSourceHandler handler = new DataSourceHandler((DataSource) datasource, username, password);
            return Proxy.newProxyInstance(datasource.getClass().getClassLoader(), datasource.getClass().getInterfaces(),
                    handler);
        } catch (Exception exception) {
            if (exception instanceof InvocationTargetException) {
                Throwable cause = exception.getCause();
                if (cause instanceof VirtualMachineError) {
                    throw (VirtualMachineError) cause;
                }
                if (cause instanceof Exception) {
                    exception = (Exception) cause;
                }
            }
            if (exception instanceof NamingException) {
                throw (NamingException) exception;
            } else {
                NamingException nx = new NamingException(exception.getMessage());
                nx.initCause(exception);
                throw nx;
            }
        }
    }

    /**
     * Simple wrapper class that will allow a user to configure a ResourceLink for a data source so that when
     * {@link javax.sql.DataSource#getConnection()} is called, it will invoke
     * {@link javax.sql.DataSource#getConnection(String, String)} with the preconfigured username and password.
     */
    public static class DataSourceHandler implements InvocationHandler {
        private final DataSource ds;
        private final String username;
        private final String password;
        private final Method getConnection;

        public DataSourceHandler(DataSource ds, String username, String password) throws Exception {
            this.ds = ds;
            this.username = username;
            this.password = password;
            getConnection = ds.getClass().getMethod("getConnection", String.class, String.class);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

            if ("getConnection".equals(method.getName()) && (args == null || args.length == 0)) {
                args = new String[] { username, password };
                method = getConnection;
            } else if ("unwrap".equals(method.getName())) {
                return unwrap((Class<?>) args[0]);
            }
            try {
                return method.invoke(ds, args);
            } catch (Throwable t) {
                if (t instanceof InvocationTargetException && t.getCause() != null) {
                    throw t.getCause();
                } else {
                    throw t;
                }
            }
        }

        public Object unwrap(Class<?> iface) throws SQLException {
            if (iface == DataSource.class) {
                return ds;
            } else {
                throw new SQLException(sm.getString("dataSourceLinkFactory.badWrapper", iface.getName()));
            }
        }

    }


}

