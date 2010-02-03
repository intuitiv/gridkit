package com.griddynamics.gridkit.coherence.patterns.message.benchmark.queue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.griddynamics.gridkit.coherence.patterns.benchmark.Dispatcher;
import com.griddynamics.gridkit.coherence.patterns.benchmark.MessageExecutionMark;
import com.griddynamics.gridkit.coherence.patterns.benchmark.stats.Accamulator;
import com.griddynamics.gridkit.coherence.patterns.benchmark.stats.InvocationServiceStats;
import com.griddynamics.gridkit.coherence.patterns.message.benchmark.MessageBenchmarkSimStats;
import com.griddynamics.gridkit.coherence.patterns.message.benchmark.PatternFacade;
import com.oracle.coherence.common.identifiers.Identifier;
import com.tangosol.net.Invocable;
import com.tangosol.net.Member;

public class QueueBenchmarkDispatcher extends Dispatcher<MessageExecutionMark,
														 InvocationServiceStats<MessageBenchmarkSimStats>>
{
	protected Map<Member,List<Identifier>> sendQueuesMap;
	protected Map<Member,List<Identifier>> receiveQueuesMap;
	
	protected final int queuesCount;
	
	protected final QueueBenchmarkWorkerParams params;
	
	protected final PatternFacade facade;
	
	protected Invocable invocableWorker;
	
	public QueueBenchmarkDispatcher(int queuesCount, QueueBenchmarkWorkerParams params, Set<Member> members, PatternFacade facade)
	{
		super(members, facade.getInvocationService());
		
		this.queuesCount = queuesCount;
		this.params      = params;
		this.facade      = facade;
	}
	
	@Override
	protected void prepare() throws Exception
	{
		sendQueuesMap    = new HashMap<Member, List<Identifier>>();
		receiveQueuesMap = new HashMap<Member, List<Identifier>>();
			
		List<Member> membersList = new ArrayList<Member>(members);
			
		//Link all members in a ring
		for (int i = 1; i <= membersList.size(); ++i)
		{
			int sender   = i - 1;
			int receiver = i % membersList.size();
	
			List<Identifier> queues = new ArrayList<Identifier>(queuesCount);
				
			for(int q = 0; q < queuesCount; ++q)
			{
				Identifier queue = facade.createQueue("queue_from_" + sender + "_to_" + receiver + "_N_" + q);
				
				queues.add(queue);
			}
				
			sendQueuesMap.put(membersList.get(sender), queues);
			receiveQueuesMap.put(membersList.get(receiver), queues);
		}
			
		invocableWorker = new QueueBenchmarkWorker(params, sendQueuesMap, receiveQueuesMap);
	}
	
	protected void calculateExecutionStatistics()
	{
		dispatcherResult.setJavaMsStats(calculateExecutionStatisticsInternal(new MessageExecutionMark.JavaMsExtractor()));
		
		dispatcherResult.setJavaNsStats(calculateExecutionStatisticsInternal(new MessageExecutionMark.JavaNsExtractor()));
		
		dispatcherResult.setCoherenceMsStats(calculateExecutionStatisticsInternal(new MessageExecutionMark.CoherenceMsExtractor()));
		
		dispatcherResult.setExecutionMarksProcessed(getDispatcherResultSise());
	}
	
	protected MessageBenchmarkSimStats calculateExecutionStatisticsInternal(MessageExecutionMark.MessageExecutionMarkTimeExtractor te)
	{
		Accamulator     latency = new Accamulator();
		
		Accamulator    sendTime = new Accamulator();
		Accamulator receiveTime = new Accamulator();
		
		int n = 0;
		
		for (List<MessageExecutionMark> l : workersResult)
		{
			for(MessageExecutionMark m : l)
			{
				n++;
				
				sendTime.add(te.getSendTime(m));
				receiveTime.add(te.getReceiveTime(m));
				
				latency.add(te.getReceiveTime(m) - te.getSendTime(m));
			}
		}
		
		MessageBenchmarkSimStats res = new MessageBenchmarkSimStats();
		
		res.setTotalTime((receiveTime.getMax() - sendTime.getMin()) / TimeUnit.SECONDS.toMillis(1));
		res.setThroughput(n / res.getTotalTime());
		
		res.setAverageLatency (latency.getMean());
		res.setLatencyVariance(latency.getVariance());
		res.setMinLatency     (latency.getMin());
		res.setMaxLatency     (latency.getMax());
		
		return res;
	}

	@Override
	protected Invocable getInvocableWorker()
	{
		return invocableWorker;
	}
	
	@Override
	protected InvocationServiceStats<MessageBenchmarkSimStats> createDispatcherResult()
	{
		return new InvocationServiceStats<MessageBenchmarkSimStats>();
	}

	@Override
	protected List<List<MessageExecutionMark>> createWorkersResult()
	{
		return new ArrayList<List<MessageExecutionMark>>();
	}
}
