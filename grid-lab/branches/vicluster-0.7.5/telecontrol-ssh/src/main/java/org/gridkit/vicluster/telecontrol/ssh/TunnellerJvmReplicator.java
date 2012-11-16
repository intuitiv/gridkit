package org.gridkit.vicluster.telecontrol.ssh;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.gridkit.internal.com.jcraft.jsch.ChannelExec;
import org.gridkit.internal.com.jcraft.jsch.JSchException;
import org.gridkit.internal.com.jcraft.jsch.Session;
import org.gridkit.util.concurrent.FutureBox;
import org.gridkit.vicluster.telecontrol.BackgroundStreamDumper;
import org.gridkit.vicluster.telecontrol.ControlledProcess;
import org.gridkit.vicluster.telecontrol.ExecCommand;
import org.gridkit.vicluster.telecontrol.JvmConfig;
import org.gridkit.vicluster.telecontrol.bootstraper.Bootstraper;
import org.gridkit.vicluster.telecontrol.bootstraper.Tunneller;
import org.gridkit.vicluster.telecontrol.bootstraper.TunnellerConnection;
import org.gridkit.vicluster.telecontrol.bootstraper.TunnellerConnection.ExecHandler;
import org.gridkit.vicluster.telecontrol.bootstraper.TunnellerConnection.SocketHandler;
import org.gridkit.zerormi.DuplexStream;
import org.gridkit.zerormi.NamedStreamPair;
import org.gridkit.zerormi.hub.RemotingHub;
import org.gridkit.zerormi.hub.RemotingHub.SessionEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TunnellerJvmReplicator implements RemoteJmvReplicator {

	private SshRemotingConfig rconfig = new SshRemotingConfig();
	private boolean initialized;
	private boolean destroyed;
	
	private Session session;
	private RemotingHub hub;
	private TunnellerConnection control;
	
	private RemoteFileCache2 jarCache;
	private String bootJarPath;
	private String tunnellerJarPath;
	
	private String tunnelHost;
	private int tunnelPort;
	
	private Logger logger;
	
	@Override
	public synchronized void configure(Map<String, String> nodeConfig) {
		rconfig.configure(nodeConfig);
		rconfig.validate();
	}

	@Override
	public synchronized String getFingerPrint() {
		return rconfig.getFingerPrint();
	}

	@Override
	public synchronized void init() throws Exception {
		if (initialized) {
			throw new IllegalStateException("Already initialized");
		}
		
		logger = LoggerFactory.getLogger(getClass().getSimpleName() + "::" + rconfig.getHost());
		
		initialized = true;
		
		try {
			SimpleSshSessionProvider sf = new SimpleSshSessionProvider();
			sf.setUser(rconfig.getAccount());
			if (rconfig.getPassword() != null) {
				sf.setPassword(rconfig.getPassword());
			}
			if (rconfig.getKeyFile() != null) {
				sf.setKeyFile(rconfig.getKeyFile());
			}
			session = sf.getSession(rconfig.getHost(), rconfig.getAccount());
			jarCache = new SftFileCache(session, rconfig.getJarCachePath(), 4);
			initRemoteClasspath();
			startTunneler();
			hub = new RemotingHub();
			initPortForwarding();
		}
		catch(Exception e) {
			destroyed = true;
			if (session != null) {
				try {
					session.disconnect();
				} catch (Exception ee) {
					// ignore
				}
			}
			throw e;
		}
	}

	private void initRemoteClasspath() throws IOException {
		List<Classpath.ClasspathEntry> classpath = Classpath.getClasspath(Thread.currentThread().getContextClassLoader());

		// random upload order improve performance if cache is on shared mount
		List<Classpath.ClasspathEntry> uploadJars = new ArrayList<Classpath.ClasspathEntry>(classpath);
		Collections.shuffle(uploadJars);
		List<String> rnames = jarCache.upload(uploadJars);
		Map<String, String> pathMap = new HashMap<String, String>();
		for(int i = 0; i != rnames.size(); ++i) {
			pathMap.put(uploadJars.get(i).getUrl().toString(), rnames.get(i));
		}

		StringBuilder remoterClasspath = new StringBuilder();
		for(Classpath.ClasspathEntry ce: classpath) {
			if (remoterClasspath.length() > 0) {
				remoterClasspath.append(' ');
			}
			remoterClasspath.append(pathMap.get(ce.getUrl().toString()));			
		}

		Manifest mf = new Manifest();
		mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
		mf.getMainAttributes().put(Attributes.Name.CLASS_PATH, remoterClasspath.toString());
		mf.getMainAttributes().put(Attributes.Name.MAIN_CLASS, Bootstraper.class.getName());
		
		byte[] bootJar = ClasspathUtils.createManifestJar(mf);
		bootJarPath = jarCache.upload(new ByteBlob("booter.jar", bootJar));

		mf.getMainAttributes().put(Attributes.Name.MAIN_CLASS, Tunneller.class.getName());

		byte[] tunnelerJar = ClasspathUtils.createManifestJar(mf);
		tunnellerJarPath = jarCache.upload(new ByteBlob("tunneller.jar", tunnelerJar));		
	}

	private void startTunneler() throws JSchException, IOException {
		ChannelExec exec = (ChannelExec) session.openChannel("exec");
		
		String cmd = rconfig.getJavaExec() + " -Xms32m -Xmx32m -jar " + tunnellerJarPath;
		exec.setCommand(cmd);
		
//		BackgroundStreamDumper.link(exec.getInputStream(), new WrapperPrintStream("[tunnel:" + rconfig.getHost() + "] ", System.out), false);
//		InputStream cin = exec.getExtInputStream();
//		OutputStream cout = exec.getOutputStream();
		// use std out
		BackgroundStreamDumper.link(exec.getExtInputStream(), new WrapperPrintStream("[tunnel:" + rconfig.getHost() + "] ", System.out), false);
		InputStream cin = exec.getInputStream();
		OutputStream cout = exec.getOutputStream();
		// this should automatically kill all processes associated with session
		exec.setPty(false);
		exec.connect();
		
		
		control = new TunnellerConnection(rconfig.getHost(), cin, cout);
	}
	
	private void initPortForwarding() throws InterruptedException, ExecutionException, IOException {
		final FutureBox<Void> box = new FutureBox<Void>();
		control.newSocket(new SocketHandler() {
			
			@Override
			public void bound(String host, int port) {
				logger.info("Remote port bound " + host + ":" + port);
				tunnelHost = host;
				tunnelPort = port;
				box.setData(null);				
			}
			
			@Override
			public void accepted(String rhost, int rport, InputStream soIn, OutputStream soOut) {
				logger.info("Inbound connection");
				handleInbound(rhost, rport, soIn, soOut);
			}
		});
		try {
			box.get(15000, TimeUnit.MILLISECONDS);
		} catch (TimeoutException e) {
			throw new RuntimeException("Failed to bind remote port due to timeout");
		}
	}
	
	protected void handleInbound(String rhost, int rport, InputStream soIn, OutputStream soOut) {
		DuplexStream ds = new NamedStreamPair("TUNNEL[" + rhost + ":" + rport + "]", soIn, soOut);
		hub.dispatch(ds);
	}
	
	private synchronized void ensureActive() {
		if (!initialized) {
			throw new IllegalStateException("Not initialized");
		}
		if (destroyed) {
			throw new IllegalStateException("Terminated");
		}
	}
	
	@Override
	public ControlledProcess createProcess(String caption, JvmConfig jvmArgs) throws IOException {
		ensureActive();
		
		ExecCommand jvmCmd = new ExecCommand(rconfig.getJavaExec());
		jvmArgs.apply(jvmCmd);
		jvmCmd.addArg("-jar").addArg(bootJarPath);
		
		RemoteControlSession session = new RemoteControlSession();
		String sessionId = hub.newSession(caption, session);
		jvmCmd.addArg(sessionId).addArg(tunnelHost).addArg(String.valueOf(tunnelPort));
		session.setSessionId(sessionId);

		exec(jvmCmd, session);
		try {
			session.started.get();
		} catch (InterruptedException e) {
			throw new RuntimeException("Interrupted");
		} catch (ExecutionException e) {
			if (e.getCause() instanceof IOException) {
				throw (IOException)e.getCause();
			}
			else {
				throw new IOException(e.getCause());
			}
		}

		return session;
	}
	
	protected void exec(ExecCommand jvmCmd, RemoteControlSession handler) throws IOException {
		handler.execId = control.exec(jvmCmd.getWorkDir(), jvmCmd.getCommandArray(), new String[0], handler);		 
	}
	
	@Override
	public synchronized void dispose() {
		if (!destroyed) {
			destroyed = true;
			hub.closeAllConnections();
			session.disconnect();
			
			hub = null;
			session = null;			
		}
	}
	
	private class RemoteControlSession extends ProcessProxy implements SessionEventListener, ControlledProcess, ExecHandler {
		
		long execId;
		String sessionId;
		ExecutorService remoteExecutorService;
		FutureBox<Void> connected = new FutureBox<Void>();
		
		@Override
		public Process getProcess() {
			return this;
		}

		@Override
		public ExecutorService getExecutionService() {
			try {
				connected.get();
			} catch (InterruptedException e) {
				throw new RuntimeException("Interrupted");
			} catch (ExecutionException e) {
				throw new RuntimeException("Execution failed", e.getCause());
			}
			return remoteExecutorService;
		}

		public void setSessionId(String sessionId) {
			this.sessionId = sessionId;
		}
		
		@Override
		public void connected(DuplexStream stream) {
			remoteExecutorService = hub.getExecutionService(sessionId);
			connected.setData(null);
			logger.info("Conntected: " + stream);
		}

		@Override
		public void interrupted(DuplexStream stream) {
			logger.info("Interrupted: " + stream);
		}

		@Override
		public void reconnected(DuplexStream stream) {
			logger.info("Reconnected: " + stream);
		}

		@Override
		public void closed() {
			kill();
		}

		@Override
		public void destroy() {
			RemotingHub hub = TunnellerJvmReplicator.this.hub;
			if (hub != null) {
				hub.closeConnection(sessionId);
			}
			kill();
		}

		protected void kill() {
			TunnellerConnection tc = control;
			try {
				if (tc != null) {
					tc.kill(execId);
				}
			} catch (IOException e) {
				// ignore
			}
		}		
	}	
	
	static class ProcessProxy extends Process implements TunnellerConnection.ExecHandler {

		protected FutureBox<Void> started = new FutureBox<Void>();
		protected FutureBox<Integer> exitCode = new FutureBox<Integer>();

		protected OutputStream stdIn;
		protected InputStream stdOut;
		protected InputStream stdErr;
		
		@Override
		public void started(OutputStream stdIn, InputStream stdOut,	 InputStream stdErr) {
			this.stdIn = stdIn;
			this.stdOut = stdOut;
			this.stdErr = stdErr;
			started.setData(null);
		}

		@Override
		public void finished(int exitCode) {
			this.exitCode.setData(exitCode);
		}

		@Override
		public OutputStream getOutputStream() {
			return stdIn;
		}

		@Override
		public InputStream getInputStream() {
			return stdOut;
		}

		@Override
		public InputStream getErrorStream() {
			return stdErr;
		}

		@Override
		public int waitFor() throws InterruptedException {
			try {
				return exitCode.get();
			} catch (ExecutionException e) {
				throw new Error("Impossible");
			}
		}

		@Override
		public int exitValue() {
			if (exitCode.isDone()) {
				try {
					return exitCode.get();
				} catch (InterruptedException e) {
					throw new Error("Impossible");
				} catch (ExecutionException e) {
					throw new Error("Impossible");
				}
			}
			else {
				throw new IllegalThreadStateException();
			}
		}

		@Override
		public void destroy() {
			//  
		}
	}
	
	static class ByteBlob implements RemoteFileCache2.Blob {

		private String filename;
		private String hash;
		private byte[] data;
		
		public ByteBlob(String filename, byte[] data) {
			this.filename = filename;
			this.data = data;
			this.hash = StreamHelper.digest(data, "SHA-1");
		}

		@Override
		public String getFileName() {
			return filename;
		}

		@Override
		public String getContentHash() {
			return hash;
		}

		@Override
		public InputStream getContent() {
			return new ByteArrayInputStream(data);
		}

		@Override
		public long size() {
			return data.length;
		}
	}
}
