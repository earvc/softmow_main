package reca;

import java.util.Observable;
import java.util.Observer;
import java.util.*;
import java.lang.Thread;
import java.lang.Object;

import java.io.*;
import java.net.*;

import org.opendaylight.controller.sal.core.Edge;
import org.opendaylight.controller.sal.flowprogrammer.IFlowProgrammerService;
import org.opendaylight.controller.sal.packet.IDataPacketService;
import org.opendaylight.controller.sal.packet.IListenDataPacket;
import org.opendaylight.controller.sal.packet.PacketResult;
import org.opendaylight.controller.sal.packet.RawPacket;
import org.opendaylight.controller.sal.routing.IRouting;
import org.opendaylight.controller.sal.topology.IListenTopoUpdates;
import org.opendaylight.controller.sal.topology.TopoEdgeUpdate;
import org.opendaylight.controller.switchmanager.ISwitchManager;
import org.opendaylight.controller.topologymanager.ITopologyManager;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class UDPClient {
	private InetAddress serverIPAddress;
	private int serverPort;
	private int clientPort;
	private DatagramSocket clientSocket;
	private DatagramPacket sendPacket;

	UDPClient(String serverIP, int serverPort, int clientPort) throws Exception {
		try {
			this.serverIPAddress = InetAddress.getByName(serverIP);
			this.serverPort = serverPort;
			this.clientPort = clientPort;
		}
		catch (UnknownHostException ex) {
			System.err.println(ex);
		}
	}

	public void openSocket() throws Exception {
		try {
			clientSocket = new DatagramSocket(clientPort);
		} 
		catch (SocketException ex) {
			System.err.println(ex);
		}
	}

	public void sendDatagram(byte[] payload) throws Exception {
		try {
			sendPacket = new DatagramPacket(payload, payload.length, serverIPAddress, serverPort);
			clientSocket.send(sendPacket);
		}
		catch (IOException ex) {
			System.err.println(ex);
		}
	}
}

class UDPServer {
	private DatagramSocket serverSocket;
	private int serverPort;
	byte[] payload = null;
	DatagramPacket receivePacket = null;

	UDPServer() {
		payload = new byte[1024];
		receivePacket = new DatagramPacket(payload, payload.length);
	}
	
	public void openSocket() throws Exception {
		try {
			serverSocket = new DatagramSocket(serverPort);
		}
		catch (SocketException ex) {
			System.err.println(ex);
		}
	}

	public DatagramPacket recvDatagram() throws Exception {
		try {
			serverSocket.receive(receivePacket);
		}
		catch (Exception ex) {
			System.err.println(ex);
		}
		return receivePacket;
	}
}

class AgentSignal {
	protected boolean packetReceived = false;

	public synchronized boolean packetReceived() {
		return this.packetReceived;
	}
}

class AgentThread extends Thread {
	private Thread t; 
	private String threadName;
	private int myPort;
	private DatagramPacket receivedPacket = null;
	private DatagramSocket listenSocket = null;

	AgentThread(String name, int myPort) {
		this.threadName = name;
		this.myPort = myPort;
		System.out.println("Creating " + threadName);
	}

	public void handlePacket(DatagramPacket receivedPacket) {
		InetAddress hostAddress = receivedPacket.getAddress();
		int hostPort = receivedPacket.getPort();
		String hostIP = hostAddress.getHostAddress();
		String message = new String(receivedPacket.getData());

		System.out.println("Received packet from host " + hostIP + ":" + hostPort);
		System.out.println("Data: " + message);
	}

	public void run() {
		System.out.println("Running " + this.threadName);
		while(true) {

			byte[] receiveData = new byte[1024];
			receivedPacket = new DatagramPacket(receiveData, receiveData.length);

			try {
				listenSocket.receive(receivedPacket);
				handlePacket(receivedPacket);
			} catch (Exception ex) {
				System.err.println(ex);
			}
		}
	}

	public void start() {
		// first initialize socket
		if (listenSocket == null) {
			try {
				listenSocket = new DatagramSocket(myPort);	
			} catch (Exception ex) {
				System.err.println(ex);
			}
		}

		System.out.println("Starting thread " + threadName);
		if (t == null) {
			t = new Thread(this, threadName);
			t.start();
		}
	}
}

public class reca extends Observable implements IListenTopoUpdates, Observer {
    private static final Logger logger = LoggerFactory
            .getLogger(reca.class);
    private ISwitchManager switchManager = null;
    private IFlowProgrammerService programmer = null;
    private IDataPacketService dataPacketService = null;
    private ITopologyManager topoManager=null;
    private IRouting routing=null;

    // Softmow Threads
	private AgentThread agent;

    void setDataPacketService(IDataPacketService s) {
        this.dataPacketService = s;
    }

    void unsetDataPacketService(IDataPacketService s) {
        if (this.dataPacketService == s) {
            this.dataPacketService = null;
        }
    }

    public void setFlowProgrammerService(IFlowProgrammerService s)
    {
        this.programmer = s;
    }

    public void unsetFlowProgrammerService(IFlowProgrammerService s) {
        if (this.programmer == s) {
            this.programmer = null;
        }
    }

    void setSwitchManager(ISwitchManager s) {
        logger.debug("SwitchManager set");
        this.switchManager = s;
    }

    void unsetSwitchManager(ISwitchManager s) {
        if (this.switchManager == s) {
            logger.debug("SwitchManager removed!");
            this.switchManager = null;
        }
    }

    void setTopologyManager(ITopologyManager t) {
        logger.debug("SwitchManager set");
        this.topoManager = t;
    }

    void unsetTopologyManager(ITopologyManager t) {
        if (this.topoManager == t) {
            logger.debug("SwitchManager removed!");
            this.topoManager = null;
        }
    }
    
    void setRouting(IRouting r) {
        logger.debug("SwitchManager set");
        this.routing = r;
    }

    void unsetRouting(IRouting r) {
        if (this.routing == r) {
            logger.debug("SwitchManager removed!");
            this.routing = null;
        }
    }
    /**
     * Function called by the dependency manager when all the required
     * dependencies are satisfied
     *
     */
    void init() {
        logger.info("Initialized");
        // Disabling the SimpleForwarding and ARPHandler bundle to not conflict with this one
        BundleContext bundleContext = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
        for(Bundle bundle : bundleContext.getBundles()) {
            if (bundle.getSymbolicName().contains("simpleforwarding")) {
                try {
                    bundle.uninstall();
                } catch (BundleException e) {
                    logger.error("Exception in Bundle uninstall "+bundle.getSymbolicName(), e); 
                }   
            }   
        }   
        initReca();
    }

    /**
     * Function called by the dependency manager when at least one
     * dependency become unsatisfied or when the component is shutting
     * down because for example bundle is being stopped.
     *
     */
    void destroy() {
    }

    /**
     * Function called by dependency manager after "init ()" is called
     * and after the services provided by the class are registered in
     * the service registry
     *
     */
    void start() {
        logger.info("Started");
    }

    /**
     * Function called by the dependency manager before the services
     * exported by the component are unregistered, this will be
     * followed by a "destroy ()" calls
     *
     */
    void stop() {
        logger.info("Stopped");
    }
    
    /**********************************************************************
     * implement this by your self
     *******************************************************************/    
    private void operatorThread(){
    	byte []msg = null;  
    	// read msg from socket
    	setChanged();
    	notifyObservers(new Msg(TYPE.APP_MSG, msg));
    }

    /**********************************************************************
     * implement this by your self
     *******************************************************************/
    private void agentThread(){
    	byte []msg = null;
    	// read msg from socket
    	setChanged();
    	notifyObservers(new Msg(TYPE.PARENT_MSG, msg));
    }

    /**********************************************************************
     * implement this by your self
     *******************************************************************/
    private void initReca(){
    	System.out.println("Init reca.");
    	this.addObserver(this);   	
    	abstraction();
    	
    	// create a thread to listen to mobility application, operatorThread()
    	agent = new AgentThread("agentThread", 9876);
    	agent.start();
    	
    	// create a thread to act as agent. agentThread()
        
        // (Topology discoery approach 2) create a thread to send link discovery message periodically
        // !!!!!!!! important !!!!!!!!!!!!!!!!!!!!
        // If you use approach 2, you also need RecA to listen to
        // packet-in message and find the LD message from the
        // Opendaylight platform
    	
    	setChanged();
    	notifyObservers(new Msg(TYPE.TOPO_CHANGE, null));
    }

   
    /**********************************************************************
     * implement this by your self
     *******************************************************************/
    private void abstraction(){
    	System.out.println("Computing Abstraction...");
    	// compute G-switch by topology
    	// use ITopologyManager topoManager
    	// https://developer.cisco.com/media/XNCJavaDocs/org/opendaylight/controller/topologymanager/ITopologyManager.html
    	
    }
    
	@Override
	public void edgeOverUtilized(Edge arg0) {
		// TODO Auto-generated method stub
	}

	@Override
	public void edgeUpdate(List<TopoEdgeUpdate> arg0) {
		// TODO Auto-generated method stub
		abstraction();
		setChanged();
		notifyObservers(new Msg(TYPE.TOPO_CHANGE, null));
	}

	@Override
	public void edgeUtilBackToNormal(Edge arg0) {
		// TODO Auto-generated method stub
		abstraction();
		setChanged();
		notifyObservers(new Msg(TYPE.TOPO_CHANGE, null));
	}

    /**********************************************************************
     * implement this by your self
     *******************************************************************/
	@Override
	public void update(Observable arg0, Object arg1) {
		// TODO Auto-generated method stub
		System.out.println("Update is called from RECA");
		Msg msg = (Msg) arg1;
		switch(msg.type){
			case TOPO_CHANGE:
				System.out.println("Topology changed, notify the parent");
				// use the agent socket to send message to parent
				break;
			case APP_MSG:
				System.out.println("Get msg from operator app, send it to the parent");
				// send the msg as packet in message to the parent
				break;
			case PARENT_MSG:
				System.out.println("Get path setup msg from parent, translate it and install rule.");
				// translate the message from G-switch to topology, compute a path and set it up
				// Use the IRouting interface : https://developer.cisco.com/media/XNCJavaDocs/org/opendaylight/controller/sal/routing/IRouting.html
			    // (If you implement Topology discovery approach 2,
                // another case of the message is LD discovery message
                // from parent).	
		}
	}

	private enum TYPE{
		TOPO_CHANGE, APP_MSG, PARENT_MSG
	}
	private class Msg{
		public TYPE type;
		public Object obj;	
		public Msg(TYPE theType, Object theObj){
			type=theType;
			obj=theObj;
		}
	}
}