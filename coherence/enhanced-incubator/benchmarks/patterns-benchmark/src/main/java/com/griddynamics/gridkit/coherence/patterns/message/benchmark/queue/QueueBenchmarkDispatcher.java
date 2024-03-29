/**
 * Copyright 2008-2010 Grid Dynamics Consulting Services, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.griddynamics.gridkit.coherence.patterns.message.benchmark.queue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.griddynamics.gridkit.coherence.patterns.benchmark.Dispatcher;
import com.griddynamics.gridkit.coherence.patterns.benchmark.executionmark.MessageExecutionMark;
import com.griddynamics.gridkit.coherence.patterns.benchmark.stats.Accamulator;
import com.griddynamics.gridkit.coherence.patterns.benchmark.stats.InvocationServiceStats;
import com.griddynamics.gridkit.coherence.patterns.message.benchmark.MessageBenchmarkStats;
import com.griddynamics.gridkit.coherence.patterns.message.benchmark.PatternFacade;
import com.oracle.coherence.common.identifiers.Identifier;
import com.tangosol.net.Invocable;
import com.tangosol.net.Member;

public class QueueBenchmarkDispatcher extends Dispatcher<MessageExecutionMark,
														 InvocationServiceStats<MessageBenchmarkStats>,
														 QueueBenchmarkParams>
{
	protected Map<Member,List<Identifier>> sendQueuesMap;
	protected Map<Member,List<Identifier>> receiveQueuesMap;
	
	protected final PatternFacade facade;
	
	protected Invocable invocableWorker;
	
	public QueueBenchmarkDispatcher(Set<Member> members, PatternFacade facade)
	{
		super(members, facade.getInvocationService());

		this.facade = facade;
	}
	
	@Override
	protected void prepare(QueueBenchmarkParams benchmarkParams) throws Exception
	{
		sendQueuesMap    = new HashMap<Member, List<Identifier>>();
		receiveQueuesMap = new HashMap<Member, List<Identifier>>();
			
		List<Member> membersList = new ArrayList<Member>(members);
			
		//Link all members in a ring
		for (int i = 1; i <= membersList.size(); ++i)
		{
			int sender   = i - 1;
			int receiver = i % membersList.size();
	
			List<Identifier> queues = new ArrayList<Identifier>(benchmarkParams.getQueuesCount());
				
			for(int q = 0; q < benchmarkParams.getQueuesCount(); ++q)
			{
				Identifier queue = facade.createQueue("queue_from_" + sender + "_to_" + receiver + "_N_" + q);
				
				queues.add(queue);
			}
				
			sendQueuesMap.put(membersList.get(sender), queues);
			receiveQueuesMap.put(membersList.get(receiver), queues);
		}
			
		invocableWorker = new QueueBenchmarkWorker(benchmarkParams, sendQueuesMap, receiveQueuesMap);
	}
	
	protected void calculateExecutionStatistics()
	{
		dispatcherResult.setJavaMsStats(calculateExecutionStatisticsInternal(new MessageExecutionMark.JavaMsExtractor()));
		
		dispatcherResult.setJavaNsStats(calculateExecutionStatisticsInternal(new MessageExecutionMark.JavaNsExtractor()));
		
		dispatcherResult.setCoherenceMsStats(calculateExecutionStatisticsInternal(new MessageExecutionMark.CoherenceMsExtractor()));
		
		dispatcherResult.setExecutionMarksProcessed(getDispatcherResultSise());
	}
	
	protected MessageBenchmarkStats calculateExecutionStatisticsInternal(MessageExecutionMark.MessageExecutionMarkTimeExtractor te)
	{
		Accamulator     latency = new Accamulator();
		
		Accamulator    sendTime = new Accamulator();
		Accamulator receiveTime = new Accamulator();
		
		int n = 0;
		
		for (Collection<MessageExecutionMark> l : workersResult)
		{
			for(MessageExecutionMark m : l)
			{
				n++;
				
				sendTime.add(te.getSendTime(m));
				receiveTime.add(te.getReceiveTime(m));
				
				latency.add(te.getReceiveTime(m) - te.getSendTime(m));
			}
		}
		
		MessageBenchmarkStats res = new MessageBenchmarkStats();
		
		res.totalTime  = (receiveTime.getMax() - sendTime.getMin()) / TimeUnit.SECONDS.toMillis(1);
		res.throughput = n / res.totalTime;
		
		res.averageLatency  = latency.getMean();
		res.latencyVariance = latency.getVariance();
		res.minLatency      = latency.getMin();
		res.maxLatency      = latency.getMax();
		
		return res;
	}

	@Override
	protected Invocable getInvocableWorker()
	{
		return invocableWorker;
	}
	
	@Override
	protected InvocationServiceStats<MessageBenchmarkStats> createDispatcherResult()
	{
		return new InvocationServiceStats<MessageBenchmarkStats>();
	}

	@Override
	protected List<Collection<MessageExecutionMark>> createWorkersResult()
	{
		return new ArrayList<Collection<MessageExecutionMark>>();
	}
}
