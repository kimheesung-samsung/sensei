/**
 * This software is licensed to you under the Apache License, Version 2.0 (the
 * "Apache License").
 *
 * LinkedIn's contributions are made under the Apache License. If you contribute
 * to the Software, the contributions will be deemed to have been made under the
 * Apache License, unless you expressly indicate otherwise. Please do not make any
 * contributions that would be inconsistent with the Apache License.
 *
 * You may obtain a copy of the Apache License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, this software
 * distributed under the Apache License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the Apache
 * License for the specific language governing permissions and limitations for the
 * software governed under the Apache License.
 *
 * © 2012 LinkedIn Corp. All Rights Reserved.  
 */

package com.senseidb.gateway.perf;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import proj.zoie.api.DataConsumer;
import proj.zoie.api.ZoieException;
import proj.zoie.impl.indexing.StreamDataProvider;
import proj.zoie.impl.indexing.ZoieConfig;

import com.senseidb.gateway.SenseiGateway;
import com.senseidb.gateway.file.LinedFileDataProviderBuilder;
import com.senseidb.gateway.kafka.KafkaDataProviderBuilder;
import com.senseidb.indexing.DataSourceFilter;

public class PerfDriver {
  
  public static final int MAX_EVENTS_PER_MIN = Integer.MAX_VALUE;
  
  public static class PerfStats{
    long runTime;
    int qps;
    volatile int numConsumed;
    
    @Override
    public String toString(){
      StringBuilder buf = new StringBuilder();
      buf.append("runtime: ").append(runTime).append(", qps: ").append(qps).append(", numConsumed: ").append(numConsumed);
      return buf.toString();
    }
  }

  private final SenseiGateway<?> _gateway;
  private final int _max;
  private PerfDriver(SenseiGateway<?> gateway,int max){
    _gateway = gateway;
    _max = max;
  }
  
  public PerfStats runPerf() throws Exception{
    StreamDataProvider<?> dataProvider = _gateway.buildDataProvider((DataSourceFilter)null, "0", null, null);
    dataProvider.setMaxEventsPerMinute(MAX_EVENTS_PER_MIN);
    final PerfStats stats = new PerfStats();
    
    dataProvider.setDataConsumer(new DataConsumer(){

      private String _version = "0";
      
      @Override
      public void consume(Collection data) throws ZoieException {
        Iterator<DataEvent<?>> iter = (Iterator<DataEvent<?>>)data.iterator();
        
        while(iter.hasNext()){
          DataEvent<?> evt = iter.next();
          _version = evt.getVersion();
        }
        stats.numConsumed+=data.size();
        System.out.println(stats.numConsumed);
      }

      @Override
      public String getVersion() {
        return _version;
      }

      @Override
      public Comparator getVersionComparator() {
        return ZoieConfig.DEFAULT_VERSION_COMPARATOR;
      }
      
    });
    
    dataProvider.start();
    long start = System.currentTimeMillis();
    stats.numConsumed = 0;
    while(stats.numConsumed<_max){
      Thread.sleep(200);
    }
    long end = System.currentTimeMillis();
    dataProvider.stop();
    stats.runTime = (end-start);
    if (stats.numConsumed>0){
      stats.qps = (int)(stats.numConsumed*1000/(stats.runTime));
    }
    return stats;
  }
  
  private static SenseiGateway<?> buildFileGateway(String file){
    LinedFileDataProviderBuilder fileGateway = new LinedFileDataProviderBuilder();
    Map<String,String> config = new HashMap<String,String>();
    config.put("file.path", file);
    fileGateway.init(config, null);
    return fileGateway;
  }
  
  /*
   * sensei.gateway.kafka.zookeeperUrl=localhost:2181
sensei.gateway.kafka.consumerGroupId=1
sensei.gateway.kafka.topic=test
sensei.gateway.kafka.timeout=3000
sensei.gateway.kafka.batchsize=1
   */
  private static SenseiGateway<?> buildKafkaGateway(String topic){
    KafkaDataProviderBuilder kafkaGateway = new KafkaDataProviderBuilder();
    Map<String,String> config = new HashMap<String,String>();
    config.put("kafka.zookeeperUrl", "localhost:2181");
    config.put("kafka.consumerGroupId", "1");
    config.put("kafka.topic", topic);
    config.put("kafka.timeout","3000");
    config.put("kafka.batchsize","10000");
    kafkaGateway.init(config, null);
    return kafkaGateway;
  }
  
  /**
   * @param args
   */
  public static void main(String[] args) throws Exception{
    String file = "/home/jwang/github/search-perf/data/cars1m.json";
    
    SenseiGateway<?> gateway = buildKafkaGateway("perfTopic");
    
    PerfDriver driver = new PerfDriver(gateway,1000000);
    
    PerfStats stats = driver.runPerf();
    System.out.println(stats);
  }
  
  

}
