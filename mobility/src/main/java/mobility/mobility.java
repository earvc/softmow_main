package mobility;

import java.util.Observable;
import java.util.Observer;
import java.util.*;
import java.lang.Thread;
import java.lang.Object;

import java.io.*;
import java.net.*;

import org.opendaylight.controller.sal.flowprogrammer.IFlowProgrammerService;
import org.opendaylight.controller.sal.packet.IDataPacketService;
import org.opendaylight.controller.sal.packet.IListenDataPacket;
import org.opendaylight.controller.sal.packet.PacketResult;
import org.opendaylight.controller.sal.packet.RawPacket;
import org.opendaylight.controller.sal.routing.IRouting;
import org.opendaylight.controller.switchmanager.ISwitchManager;
import org.opendaylight.controller.topologymanager.ITopologyManager;
import org.opendaylight.controller.topologymanager.*;
import org.opendaylight.controller.sal.core.*;
import org.opendaylight.controller.sal.topology.ITopologyService;
import org.opendaylight.controller.switchmanager.ISwitchManager;
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
	
	public void openSocket() throws Exception {
		try {
			serverSocket = new DatagramSocket(serverPort);
		}
		catch (SocketException ex) {
			System.err.println(ex);
		}
	}

	public DatagramPacket recvDatagram(byte[] payload) throws Exception {
		DatagramPacket receivePacket = null;
		
		try {
			receivePacket =  new DatagramPacket(payload, payload.length);
		}
		catch (Exception ex) {
			System.err.println(ex);
		}
		return receivePacket;
	}
}
			
class ThreadSignal { 
	protected boolean flag = false;

	public synchronized boolean getFlag() {
		return this.flag;
	}

	public synchronized void setFlag(boolean newFlagVal) {
		this.flag = newFlagVal;
	}
}

class MobilityThreads extends Thread {
	private Thread t;
	private String threadName;
	private ThreadSignal sig;

	MobilityThreads(String name, ThreadSignal signal) {
		this.threadName = name;
		this.sig = signal;
		System.out.println("Creating " + threadName);
	}

	public void run() {
		System.out.println("Running " + threadName);
		
		try {
			while (true) {
				synchronized(sig) {
					sig.wait();
					if (sig.flag) {
						System.out.println("====Thread fired from signal====");
						sig.setFlag(false);
					}
				}
			}
		} catch (InterruptedException e) {
			System.out.println("Thread " + threadName + " interrupt exception.");
		}
	}

	public void start() {
		System.out.println("Starting " + threadName);
		if (t == null) {
			t = new Thread (this, threadName);
			t.start();
		}
	}
}

public class mobility extends Observable implements IListenDataPacket, Observer {
    private static final Logger logger = LoggerFactory
            .getLogger(mobility.class);
    private IFlowProgrammerService programmer = null;
    private IDataPacketService dataPacketService = null;
    private ITopologyManager topoManager;
    private ITopologyService topoService;
    private ISwitchManager  switchManager;
    private IRouting routing = null;

    private MobilityThreads sendToRecA = null;
    private ThreadSignal recASig = null;
    private UDPClient mobilityClient = null;

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
        initMobility();
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

    /***************************************************
     * Implement this by yourself
     ***************************************************/    
    private void recaThread(){
    	byte []msg=null;
    	// read msg from reca
    	setChanged();
    	notifyObservers(new Msg(TYPE.RECA_MSG, msg));
    }
    
    /***************************************************
     * Implement this by yourself
     ***************************************************/    
    private void initMobility(){
    	System.out.println("Initializing Mobility module");

/*		if (recASig == null)
			recASig = new ThreadSignal();

    	sendToRecA = new MobilityThreads("sendToRecA", recASig);
    	sendToRecA.start();
    	this.addObserver(this); */
    }
    
    private void discoverTestFunc() {
    	Map<Node,Set<Edge>> netMap;
    	Set<NodeConnector> hostList;
    	Set<Node> allNodes;
    	netMap = topoManager.getNodeEdges();
    	hostList = topoManager.getNodeConnectorWithHost();
    	allNodes = switchManager.getNodes();
    	System.out.println("Number of NodeConnectors: " + Integer.toString(hostList.size()));
    	System.out.println("Number of Node/Edge values: " + Integer.toString(netMap.size()));
    	System.out.println("Number of Nodes: " + Integer.toString(allNodes.size()));
    	System.out.println("Discovered network");
    	printNodesAndEdges(netMap);
	}

	private void printNodesAndEdges(Map<Node, Set<Edge>> netMap) {
		List<Node> nodeList = new ArrayList<Node>(switchManager.getNodes());  // list of all nodes in network
		List<Edge> edgeList;

		for (Node node_item : nodeList) {
			System.out.println("NodeType: " + node_item.getType() + ", NodeID: " + node_item.getNodeIDString());
			edgeList = new ArrayList<Edge>(netMap.get(node_item));
			for (Edge edge_item : edgeList) {
				System.out.println("\tEdge: " + edge_item.toString());
			}
		}
	}
    
	@Override
	public PacketResult receiveDataPacket(RawPacket arg0) {
		setChanged();
		//notifyObservers(new Msg(TYPE.PKTIN_MSG, arg0));
		return PacketResult.KEEP_PROCESSING;
	}

    /***************************************************
     * Implement this by yourself
     ***************************************************/    
	@Override
	public void update(Observable arg0, Object arg1) {
		// TODO Auto-generated method stub
		Msg msg=(Msg)arg1;
		switch(msg.type){
		case PKTIN_MSG:
			System.out.println("---------------------------------------");
			discoverTestFunc();
			synchronized (recASig) {
				recASig.setFlag(true);
				recASig.notify();
			}

			// use routing to get the route,
			// if no path, send to reca
			break;
		case RECA_MSG:
			System.out.println("Mobility get a RecA msg");
			// leav this prcessing blank, I leave the space for future extension
		}
	}
	private enum TYPE{
		PKTIN_MSG, RECA_MSG, APP_MSG
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
