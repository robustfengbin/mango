/*
 * Copyright 2014 mango.jfaster.org
 *
 * The Mango Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.jfaster.mango.operator;

import org.jfaster.mango.cache.CacheHandler;
import org.jfaster.mango.cache.Day;
import org.jfaster.mango.datasource.factory.SimpleDataSourceFactory;
import org.jfaster.mango.support.*;
import org.jfaster.mango.support.model4table.User;
import org.jfaster.mango.util.reflect.MethodDescriptor;
import org.jfaster.mango.util.reflect.ParameterDescriptor;
import org.jfaster.mango.util.reflect.TypeToken;
import org.junit.Test;

import javax.sql.DataSource;
import java.lang.annotation.Annotation;
import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * @author ash
 */
public class CacheableBatchUpdateOperatorTest {

    @Test
    public void testBatchUpdate() throws Exception {
        TypeToken<List<User>> pt = new TypeToken<List<User>>() {};
        TypeToken<int[]> rt = TypeToken.of(int[].class);
        String srcSql = "update user set name=:1.name where id=:1.id";
        Operator operator = getOperator(pt, rt, srcSql, new CacheHandlerAdapter() {
            @Override
            public void delete(Set<String> keys) {
                Set<String> set = new HashSet<String>();
                set.add("user_100");
                set.add("user_200");
                assertThat(keys, equalTo(set));
            }
        }, new MockCacheBy("id"));

        StatsCounter sc = new StatsCounter();
        operator.setStatsCounter(sc);
        operator.setJdbcOperations(new JdbcOperationsAdapter() {
            @Override
            public int[] batchUpdate(DataSource ds, String sql, List<Object[]> batchArgs) {
                String descSql = "update user set name=? where id=?";
                assertThat(sql, equalTo(descSql));
                assertThat(batchArgs.size(), equalTo(2));
                assertThat(batchArgs.get(0)[0], equalTo((Object) "ash"));
                assertThat(batchArgs.get(0)[1], equalTo((Object) 100));
                assertThat(batchArgs.get(1)[0], equalTo((Object) "lucy"));
                assertThat(batchArgs.get(1)[1], equalTo((Object) 200));
                return new int[] {1};
            }
        });

        List<User> users = Arrays.asList(new User(100, "ash"), new User(200, "lucy"));
        operator.execute(new Object[]{users});
        assertThat(sc.snapshot().evictionCount(), equalTo(2L));
    }

    private Operator getOperator(TypeToken<?> pt, TypeToken<?> rt, String srcSql,
                                 CacheHandler ch, MockCacheBy cacheBy) throws Exception {
        List<Annotation> pAnnos = new ArrayList<Annotation>();
        pAnnos.add(cacheBy);
        ParameterDescriptor p = new ParameterDescriptor(0, pt.getType(), pt.getRawType(), pAnnos);
        List<ParameterDescriptor> pds = Arrays.asList(p);

        List<Annotation> methodAnnos = new ArrayList<Annotation>();
        methodAnnos.add(new MockDB());
        methodAnnos.add(new MockCache("user_", Day.class));
        methodAnnos.add(new MockSQL(srcSql));
        MethodDescriptor md = new MethodDescriptor(rt.getType(), rt.getRawType(), methodAnnos, pds);

        OperatorFactory factory = new OperatorFactory(
                new SimpleDataSourceFactory(Config.getDataSource()),
                ch, new InterceptorChain(), new InterceptorChain());

        Operator operator = factory.getOperator(md);
        return operator;
    }

}