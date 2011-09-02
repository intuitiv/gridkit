import java.io.IOException;

import org.gridkit.search.gemfire.LuceneQueryExecutor;

import com.gemstone.gemfire.addon.pogo.KeyTypeManager;
import com.gemstone.gemfire.cache.Cache;
import com.gemstone.gemfire.cache.CacheFactory;
import com.gemstone.gemfire.cache.PartitionAttributes;
import com.gemstone.gemfire.cache.PartitionAttributesFactory;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.RegionShortcut;
import com.gemstone.gemfire.cache.execute.FunctionService;
import com.gemstone.gemfire.cache.query.IndexExistsException;
import com.gemstone.gemfire.cache.query.IndexType;
import com.gemstone.gemfire.cache.server.CacheServer;
import com.gemstone.gemfire.internal.cache.CacheServerLauncher;

import domain.ObjectX;


public class StartFunctionNode {
	
	public static void main(String[] args) throws InterruptedException, IOException {
		
		System.out.println(CacheServerLauncher.class.getSimpleName() + ".PRINT_LAUNCH_COMMAND");
		
		KeyTypeManager.registerKeyType(ObjectX.getKeyType());
		
		Cache cache = new CacheFactory()
        .set("mcast-port", "0")
        .set("locators", "127.0.0.1[5555]")
        .create();
		
//		CacheServer server = cache.addCacheServer();
//		server.start();
		
		
//		PartitionAttributes pa = new PartitionAttributesFactory()
//        .setLocalMaxMemory(300)
//        .setRedundantCopies(1)
//        .setTotalNumBuckets(100).create();
//		
//		Region region = cache.createRegionFactory(RegionShortcut.PARTITION)
//        .setPartitionAttributes(pa)
//        .create("maplite");
//
//		Region region2 = cache.createRegionFactory(RegionShortcut.PARTITION)
//		.setPartitionAttributes(pa)
//		.create("pogo");
		
//		System.out.println("Creating index");
//		try {
//			cache.getQueryService().createIndex("mapliteIndex", IndexType.FUNCTIONAL, "value.get('textField')", "/maplite.values value");
//			cache.getQueryService().createIndex("pogoIndex", IndexType.FUNCTIONAL, "value.getText()", "/pogo.values value");
//		} catch (IndexExistsException e) {
//			// ignore
//		} catch (Exception e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}

		LuceneQueryExecutor lqe = new LuceneQueryExecutor();
		FunctionService.registerFunction(lqe);
		
		System.out.println("Function node started");
		while(true) {
			Thread.sleep(5000);
		}
	}
}
