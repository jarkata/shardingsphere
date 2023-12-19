/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.data.pipeline.core.checker;

import org.apache.shardingsphere.data.pipeline.core.exception.job.PrepareJobWithInvalidConnectionException;
import org.apache.shardingsphere.data.pipeline.core.exception.job.PrepareJobWithTargetTableNotEmptyException;
import org.apache.shardingsphere.data.pipeline.core.importer.ImporterConfiguration;
import org.apache.shardingsphere.data.pipeline.core.ingest.dumper.context.mapper.TableAndSchemaNameMapper;
import org.apache.shardingsphere.data.pipeline.core.sqlbuilder.PipelineCommonSQLBuilder;
import org.apache.shardingsphere.infra.database.core.spi.DatabaseTypedSPILoader;
import org.apache.shardingsphere.infra.database.core.type.DatabaseType;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;

/**
 * Data source check engine.
 */
public final class DataSourceCheckEngine {
    
    private final DialectDataSourceChecker checker;
    
    private final PipelineCommonSQLBuilder sqlBuilder;
    
    public DataSourceCheckEngine(final DatabaseType databaseType) {
        checker = DatabaseTypedSPILoader.findService(DialectDataSourceChecker.class, databaseType).orElse(null);
        sqlBuilder = new PipelineCommonSQLBuilder(databaseType);
    }
    
    /**
     * Check source data source.
     * 
     * @param dataSource to be checked source data source
     */
    public void checkSourceDataSource(final DataSource dataSource) {
        Collection<DataSource> dataSources = Collections.singleton(dataSource);
        checkConnection(dataSources);
        checkPrivilege(dataSources);
        checkVariable(dataSources);
    }
    
    /**
     * Check target data source.
     *
     * @param dataSource to be checked target data source
     * @param importerConfig importer configuration
     */
    public void checkTargetDataSource(final DataSource dataSource, final ImporterConfiguration importerConfig) {
        Collection<DataSource> dataSources = Collections.singleton(dataSource);
        checkConnection(dataSources);
        checkTargetTable(dataSources, importerConfig.getTableAndSchemaNameMapper(), importerConfig.getLogicTableNames());
    }
    
    /**
     * Check data source connections.
     *
     * @param dataSources data sources
     * @throws PrepareJobWithInvalidConnectionException prepare job with invalid connection exception
     */
    public void checkConnection(final Collection<? extends DataSource> dataSources) {
        try {
            for (DataSource each : dataSources) {
                each.getConnection().close();
            }
        } catch (final SQLException ex) {
            throw new PrepareJobWithInvalidConnectionException(ex);
        }
    }
    
    /**
     * Check table is empty.
     *
     * @param dataSources data sources
     * @param tableAndSchemaNameMapper mapping
     * @param logicTableNames logic table names
     * @throws PrepareJobWithInvalidConnectionException prepare job with invalid connection exception
     */
    // TODO rename to common usage name
    // TODO Merge schemaName and tableNames
    public void checkTargetTable(final Collection<? extends DataSource> dataSources, final TableAndSchemaNameMapper tableAndSchemaNameMapper, final Collection<String> logicTableNames) {
        try {
            for (DataSource each : dataSources) {
                for (String tableName : logicTableNames) {
                    if (!checkEmpty(each, tableAndSchemaNameMapper.getSchemaName(tableName), tableName)) {
                        throw new PrepareJobWithTargetTableNotEmptyException(tableName);
                    }
                }
            }
        } catch (final SQLException ex) {
            throw new PrepareJobWithInvalidConnectionException(ex);
        }
    }
    
    private boolean checkEmpty(final DataSource dataSource, final String schemaName, final String tableName) throws SQLException {
        String sql = sqlBuilder.buildCheckEmptySQL(schemaName, tableName);
        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement preparedStatement = connection.prepareStatement(sql);
                ResultSet resultSet = preparedStatement.executeQuery()) {
            return !resultSet.next();
        }
    }
    
    /**
     * Check user privileges.
     *
     * @param dataSources data sources
     */
    public void checkPrivilege(final Collection<? extends DataSource> dataSources) {
        if (null == checker) {
            return;
        }
        for (DataSource each : dataSources) {
            checker.checkPrivilege(each);
        }
    }
    
    /**
     * Check data source variables.
     *
     * @param dataSources data sources
     */
    public void checkVariable(final Collection<? extends DataSource> dataSources) {
        if (null == checker) {
            return;
        }
        for (DataSource each : dataSources) {
            checker.checkVariable(each);
        }
    }
}