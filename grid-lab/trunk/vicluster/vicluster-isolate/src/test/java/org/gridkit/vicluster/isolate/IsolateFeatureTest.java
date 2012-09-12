package org.gridkit.vicluster.isolate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.Remote;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.gridkit.util.concurrent.FutureEx;
import org.gridkit.vicluster.ViGroup;
import org.gridkit.vicluster.ViNode;
import org.gridkit.vicluster.VoidCallable;
import org.gridkit.vicluster.isolate.IsolateViNode;
import org.gridkit.vicluster.isolate.OldIsolate;
import org.gridkit.vicluster.isolate.StaticVarHost;
import org.junit.After;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

public class IsolateFeatureTest {

	private static Map<String, String> ISOLATE_PROPS = new HashMap<String, String>();
	static {
		ISOLATE_PROPS.put("isolate:package:org.gridkit", "");
	}
	
	ViGroup hosts = new ViGroup();
	
	private IsolateViNode createIsolateViHost(String name) {
		IsolateViNode viHost = new IsolateViNode(name);
		hosts.addNode(viHost);
		return viHost;
	}
	
	@After
	public void cleanIsolates() {
		hosts.shutdown();
		hosts = new ViGroup();
	}

	@Test
	public void verify_isolated_static_with_void_callable() {
		
		IsolateViNode viHost1 = createIsolateViHost("node-1");
		IsolateViNode viHost2 = createIsolateViHost("node-2");
		
		ViGroup group = ViGroup.group(viHost1, viHost2);
		group.setProps(ISOLATE_PROPS);
		
		viHost1.exec(new VoidCallable() {
			@Override
			public void call() throws Exception {
				StaticVarHost.TEST_STATIC_VAR = "isolate 1";
			}
		});

		viHost2.exec(new VoidCallable() {
			@Override
			public void call() throws Exception {
				StaticVarHost.TEST_STATIC_VAR = "isolate 2";
			}
		});
		
		List<String> results = group.massExec(new Callable<String>() {
			@Override
			public String call() throws Exception {
				return StaticVarHost.TEST_STATIC_VAR;
			}
		});
		
		Assert.assertEquals("Static variable should be different is different isolartes", "[isolate 1, isolate 2]", results.toString());
	}

	@Test
	public void verify_isolated_static_with_callable() {
		
		IsolateViNode viHost1 = createIsolateViHost("node-1");
		IsolateViNode viHost2 = createIsolateViHost("node-2");
		
		ViGroup group = ViGroup.group(viHost1, viHost2);
		group.setProps(ISOLATE_PROPS);
		
		viHost1.exec(new Callable<Void>() {
			@Override
			public Void call() throws Exception {
				StaticVarHost.TEST_STATIC_VAR = "isolate 1";
				return null;
			}
		});
		
		viHost2.exec(new Callable<Void>() {
			@Override
			public Void call() throws Exception {
				StaticVarHost.TEST_STATIC_VAR = "isolate 2";
				return null;
			}
		});
		
		List<String> results = group.massExec(new Callable<String>() {
			@Override
			public String call() throws Exception {
				return StaticVarHost.TEST_STATIC_VAR;
			}
		});
		
		Assert.assertEquals("Static variable should be different is different isolartes", "[isolate 1, isolate 2]", results.toString());
	}

	@Test
	public void verify_isolated_static_with_runnable() {
		
		IsolateViNode viHost1 = createIsolateViHost("node-1");
		IsolateViNode viHost2 = createIsolateViHost("node-2");
		
		ViGroup group = ViGroup.group(viHost1, viHost2);
		group.setProps(ISOLATE_PROPS);
		
		viHost1.exec(new Runnable() {
			@Override
			public void run() {
				StaticVarHost.TEST_STATIC_VAR = "isolate 1";
			}
		});
		
		viHost2.exec(new Runnable() {
			@Override
			public void run() {
				StaticVarHost.TEST_STATIC_VAR = "isolate 2";
			}
		});
		
		List<String> results = group.massExec(new Callable<String>() {
			@Override
			public String call() throws Exception {
				return StaticVarHost.TEST_STATIC_VAR;
			}
		});
		
		Assert.assertEquals("Static variable should be different is different isolartes", "[isolate 1, isolate 2]", results.toString());
	}
	
	@Test
	public void verify_class_exclusion() {
		
		IsolateViNode viHost1 = createIsolateViHost("node-1");
		IsolateViNode viHost2 = createIsolateViHost("node-2");
		
		ViGroup group = ViGroup.group(viHost1, viHost2);
		group.setProps(ISOLATE_PROPS);
		
		IsolateViNode.excludeClass(group, StaticVarHost.class);
		
		viHost1.exec(new Runnable() {
			@Override
			public void run() {
				StaticVarHost.TEST_STATIC_VAR = "isolate 1";
			}
		});
		
		viHost2.exec(new Runnable() {
			@Override
			public void run() {
				StaticVarHost.TEST_STATIC_VAR = "isolate 2";
			}
		});
		
		List<String> results = group.massExec(new Callable<String>() {
			@Override
			public String call() throws Exception {
				return StaticVarHost.TEST_STATIC_VAR;
			}
		});
		
		Assert.assertEquals("Static variable should be different is different isolartes", "[isolate 2, isolate 2]", results.toString());
	}	
	
	@Test
	public void verify_property_isolation() throws Exception {
		
		ViNode node1 = createIsolateViHost("node-1");
		ViNode node2 = createIsolateViHost("node-2");

		ViGroup.group(node1, node2).setProps(ISOLATE_PROPS);

		node1.exec(new Runnable() {
			@Override
			public void run() {
				System.setProperty("local-prop", "Isolate1");				
			}
		});

		node2.exec(new Runnable() {
			@Override
			public void run() {
				System.setProperty("local-prop", "Isolate2");				
			}
		});

		node1.exec(new Runnable() {
			@Override
			public void run() {
				Assert.assertEquals("Isolate1", System.getProperty("local-prop"));				
			}
		});
		
		node2.exec(new Runnable() {
			@Override
			public void run() {
				Assert.assertEquals("Isolate2", System.getProperty("local-prop"));				
			}
		});		

		final String xxx = new String("Hallo from Isolate2");
		node2.exec(new Runnable() {
			@Override
			public void run() {
				Assert.assertEquals("Hallo from Isolate2", xxx);				
			}
		});		
		
		Assert.assertNull(System.getProperty("local-prop"));
	}
	
	@Test
	public void verify_export_feature() {
		
		ViNode node = createIsolateViHost("node-1");
		node.setProps(ISOLATE_PROPS);
		
		Echo echo = node.exec(new Callable<Echo>() {			
			@Override
			public Echo call() throws Exception {
				return new EchoPropServer();
			}
		});
		
		node.setProp("echo", "ABC");
		
		Assert.assertEquals("ABC", echo.echo("echo"));
		
		node.setProp("echo", "123");
		
		Assert.assertEquals("123", echo.echo("echo"));
	}

	@Test
	public void verify_transitive_export() {
		
		ViNode node1 = createIsolateViHost("node-1");
		node1.setProps(ISOLATE_PROPS);
		ViNode node2 = createIsolateViHost("node-2");
		node2.setProps(ISOLATE_PROPS);
		
		final Echo echo = node1.exec(new Callable<Echo>() {			
			@Override
			public Echo call() throws Exception {
				return new EchoPropServer();
			}
		});
		
		node1.setProp("echo", "ABC");
		
		node2.exec(new Runnable() {
			
			@Override
			public void run() {
				Assert.assertEquals("ABC", echo.echo("echo"));
			}
		});
		
		node1.setProp("echo", "123");

		node2.exec(new Runnable() {
			
			@Override
			public void run() {
				Assert.assertEquals("123", echo.echo("echo"));
			}
		});		
	}
	
	@Test
	public void verify_exec_stack_trace_locality() {

		ViNode node = createIsolateViHost("node-1");
		node.setProps(ISOLATE_PROPS);
		
		try {
			node.exec(new Runnable() {
				@Override
				public void run() {
					throw new IllegalArgumentException("test");
				}
			});
			Assert.assertFalse("Should throw an exception", true);
		}
		catch(IllegalArgumentException e) {
			e.printStackTrace();
			Assert.assertEquals(e.getMessage(), "test");
			assertLocalStackTrace(e);
		}
	}

	@Test(expected=IllegalArgumentException.class)
	public void verify_late_classpath_config_error() {
		
		ViNode node = createIsolateViHost("node-1");
		node.exec(new Runnable() {			
			@Override
			public void run() {
				// do nothing				
			}
		});
		
		node.setProps(ISOLATE_PROPS);
		Assert.assertFalse("Should not reach here", true);
	}

	@Test(expected=IllegalArgumentException.class)
	public void verify_late_classpath_config_error2() {
		
		ViNode node = createIsolateViHost("node-1");
		node.setProps(ISOLATE_PROPS);
		node.exec(new Runnable() {			
			@Override
			public void run() {
				// do nothing				
			}
		});
		
		node.setProp("isolate:shared:" + FutureEx.class.getName(), "");
		Assert.assertFalse("Should not reach here", true);
	}

	private void assertLocalStackTrace(IllegalArgumentException e) {
		Exception local = new Exception();
		int depth = local.getStackTrace().length - 2; // ignoring local and calling frame
		Assert.assertEquals(
				printStackTop(e.getStackTrace(),depth), 
				printStackTop(new Exception().getStackTrace(), depth)
		);
	}
	
	private static String printStackTop(StackTraceElement[] stack, int depth) {
		StringBuilder sb = new StringBuilder();
		int n = stack.length - depth;
		n = n < 0 ? 0 : n;
		for(int i = n; i != stack.length; ++i) {
			sb.append(stack[i]).append("\n");
		}
		return sb.toString();
	}
	
	// TODO expose export feature
	@Test @Ignore("Feature is missing")
	public void test_stack_trace2() {

		OldIsolate is1 = new OldIsolate("node-1", "com.tangosol", "org.gridkit");
		is1.start();
		
		try {
			Runnable r = is1.export(new Callable<Runnable>() {
				public Runnable call() {
					return 	new Runnable() {
						@Override
						public void run() {
							throw new IllegalArgumentException("test2");
						}
					};
				}
			});

			r.run();
			
			Assert.assertFalse("Should throw an exception", true);
		}
		catch(IllegalArgumentException e) {
			e.printStackTrace();
		}
	}	
	
	@Test
	public void test_classpath_extention() throws MalformedURLException {
		
		ViNode node = createIsolateViHost("test-node");
		node.setProps(ISOLATE_PROPS);
		
		URL jar = getClass().getResource("/marker-override.jar");
		Assert.assertNotNull("marker-override.jar schould be present in classpath", jar);
		URL path = new URL("jar:" + jar.toString() + "!/");
		IsolateViNode.addToClasspath(node, path);
		
		node.exec(new CheckMarker("Marker from jar"));
		
		new CheckMarker("Default marker").run();
	}

	@Test(expected = NoClassDefFoundError.class)
	public void test_classpath_limiting() throws MalformedURLException {
		ViNode node = createIsolateViHost("test-node");
		node.setProps(ISOLATE_PROPS);
		IsolateViNode.includePackage(node, "org.junit");
		
		URL url = getClass().getResource("/org/junit/Assert.class");
		Assert.assertNotNull(url);
		
		String jarUrl = url.toString();
		jarUrl = jarUrl.substring(0, jarUrl.lastIndexOf('!') + 2);
		IsolateViNode.removeFromClasspath(node, new URL(jarUrl));
		
		node.exec(new Runnable() {
			@Override
			public void run() {
				// should throw NoClassDefFoundError because junit was removed from isolate classpath
				Assert.assertTrue(true);
			}
		});		
	}
	
	@SuppressWarnings("serial")
	public static class CheckMarker implements Runnable, Serializable {

		private String expected;
		
		public CheckMarker(String expected) {
			this.expected = expected;
		}

		@Override
		public void run() {
			try {
				URL url = getClass().getResource("/marker.txt");
				Assert.assertNotNull(url);
				BufferedReader r = new BufferedReader(new InputStreamReader(url.openStream()));
				if (expected != null) {
					Assert.assertEquals(r.readLine(), expected);
				}
				r.close();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}			
		}
	}
	
	@Test
	public void test_annonimous_primitive_in_args() {
		
		ViNode node = createIsolateViHost("test_annonimous_primitive_in_args");
		node.setProps(ISOLATE_PROPS);
		
		final boolean fb = trueConst();
		final int fi = int_10();
		final double fd = double_10_1();
		
		node.exec(new Callable<Void>() {

			@Override
			public Void call() throws Exception {
				Assert.assertEquals("fb", true, fb);
				Assert.assertEquals("fi", 10, fi);
				Assert.assertEquals("fd", 10.1d, fd, 0d);
				return null;
			}			
		});
	}

	private double double_10_1() {
		return 10.1d;
	}

	private int int_10() {
		return 9 + 1;
	}

	private boolean trueConst() {
		return true & true;
	}	

	public static interface Echo extends Remote {
		
		public String echo(String text);
		
	}
	
	public static class EchoPropServer implements Echo {
		@Override
		public String echo(String text) {
			String propVal = System.getProperty(text);			
			return propVal;
		}		
	}	
}
