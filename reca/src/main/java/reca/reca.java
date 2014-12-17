package reca;

import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Stack;
import java.util.*;
import java.lang.Thread;
import java.lang.Object;

import java.io.*;
import java.net.*;

import org.opendaylight.controller.sal.utils.ServiceHelper;
import org.opendaylight.controller.sal.core.Edge;
import org.opendaylight.controller.sal.core.ConstructionException;
import org.opendaylight.controller.sal.core.Node;
import org.opendaylight.controller.sal.core.Name;
import org.opendaylight.controller.sal.core.NodeConnector;
import org.opendaylight.controller.sal.core.Property;
import org.opendaylight.controller.sal.core.UpdateType;
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
import org.opendaylight.controller.connectionmanager.IConnectionManager;
import org.opendaylight.controller.sal.topology.ITopologyService;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


class AgentThreadReceive extends Thread {
	private Thread t; 
	private String threadName;
	private int myPort;
	private DatagramPacket receivedPacket = null;
	private DatagramSocket listenSocket = null;
	private IDataPacketService agentPacketService; 

	// members for inter-link discovery
	private Stack ldStack = null;

	AgentThreadReceive(String name, int myPort) {
		this.threadName = name;
		this.myPort = myPort;
		System.out.println("Creating " + threadName);
	}

	public static byte[] serialize(Object obj) {
		ByteArrayOutputStream out = null;
		ObjectOutputStream os = null;
		
		try {
			out = new ByteArrayOutputStream();
			os = new ObjectOutputStream(out);
			os.writeObject(obj);
		} catch (Exception ex) {
			System.err.println(ex);
		}
		return out.toByteArray();
	}

	public static Object deserialize(byte[] data) {
		ByteArrayInputStream in = null;
		ObjectInputStream is = null;
		Object returnObj = null;

		try {
			in = new ByteArrayInputStream(data);
			is = new ObjectInputStream(in);
			returnObj = is.readObject();
		} catch (Exception ex) {
			System.err.println(ex);
		}
		return returnObj;
	}


	public NodeConnector getOutgoingNodeConnector(byte[] ldData) {
		NodeConnector outgoingNodeConnector = null;
		return outgoingNodeConnector;
	}

	public void sendMessageToSwitch(byte[] sendData, NodeConnector outgoingNodeConnector) {
		try {
			RawPacket destPacket = new RawPacket(sendData);
			destPacket.setOutgoingNodeConnector(outgoingNodeConnector);
			agentPacketService.transmitDataPacket(destPacket);
		} catch (Exception ex) {
			System.err.println(ex);
		}
	}

	public void processLdStack(Stack ldStack) {
		System.out.println("Processsing stack");
	}
		
	public void handlePacket(DatagramPacket receivedPacket) {
		InetAddress hostAddress = receivedPacket.getAddress();
		int hostPort = receivedPacket.getPort();
		String hostIP = hostAddress.getHostAddress();
		byte [] ldData = receivedPacket.getData();

		// get stack from data
		ldStack = (Stack) deserialize(ldData);

		// process stack
		processLdStack(ldStack);

		// get outgoing node connector
		NodeConnector outgoingNode = getOutgoingNodeConnector(ldData);

		// serialize ldStack for sending to switch
		byte [] ldDataToSend = serialize(ldStack);

		// send data to switch to forward for link discovery
		sendMessageToSwitch(ldDataToSend, outgoingNode);

		System.out.println("Received packet from host " + hostIP + ":" + hostPort);
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

		// create output and input streams for sending serializable data

		System.out.println("Starting thread " + threadName);
		if (t == null) {
			t = new Thread(this, threadName);
			t.start();
		}
	}
}

class AgentSendParent {
	private String parentIP;
	private int parentPort;
	private DatagramSocket parentSocket = null;
	private DatagramPacket packetToSend = null;

	AgentSendParent(String parentIP, int parentPort) {
		this.parentIP = parentIP;
		this.parentPort = parentPort;
		
		// create socket to export abstraction to parent
		try {
			parentSocket = new DatagramSocket();
		} catch (Exception ex) {
			System.err.println(ex);
		}
	}

	public static byte[] serialize(Object obj) {
		ByteArrayOutputStream out = null;
		ObjectOutputStream os = null;
		
		try {
			out = new ByteArrayOutputStream();
			os = new ObjectOutputStream(out);
			os.writeObject(obj);
		} catch (Exception ex) {
			System.err.println(ex);
		}
		return out.toByteArray();
	}

	public static Object deserialize(byte[] data) {
		ByteArrayInputStream in = null;
		ObjectInputStream is = null;
		Object returnObj = null;

		try {
			in = new ByteArrayInputStream(data);
			is = new ObjectInputStream(in);
			returnObj = is.readObject();
		} catch (Exception ex) {
			System.err.println(ex);
		}
		return returnObj;
	}


	public void sendAbstraction(byte [] dataToSend) {
		try {
			InetAddress IPAddress = InetAddress.getByName(parentIP);
			packetToSend = new DatagramPacket(dataToSend, dataToSend.length, IPAddress, parentPort);
			parentSocket.send(packetToSend);
			System.out.println("Sending packet with abstraction to parent " + parentIP + ":" + parentPort);
		} catch (Exception ex) {
			System.err.println(ex);
		}
	}
}


public class reca extends Observable implements IListenTopoUpdates, Observer {
    private static final Logger logger = LoggerFactory
            .getLogger(reca.class);
    private ISwitchManager switchManager = null;
    private IFlowProgrammerService programmer = null;
    private IDataPacketService dataPacketService = null;
    private ITopologyManager topoManager = null;
    private IRouting routing = null;
    private ITopologyService topoService = null;
    private IConnectionManager connectionManager = (IConnectionManager) ServiceHelper.getGlobalInstance(IConnectionManager.class, this);

    // Softmow objects and variables
	private AgentThreadReceive agentReceive;
	private AgentSendParent agentSend;
	private String myID;

    private int nb_ports = 0;
    private ConcurrentHashMap<Integer,Node> inNodesMap = new ConcurrentHashMap<Integer,Node>();
    private ConcurrentHashMap<Integer,Node> outNodesMap = new ConcurrentHashMap<Integer,Node>();
    private ConcurrentHashMap<Integer,NodeConnector> inNodeConnectorsMap = new ConcurrentHashMap<Integer,NodeConnector>();
    private ConcurrentHashMap<Integer,NodeConnector> outNodeConnectorsMap = new ConcurrentHashMap<Integer,NodeConnector>();

    void setDataPacketService(IDataPacketService s) {
        this.dataPacketService = s;
    }

    void unsetDataPacketService(IDataPacketService s) {
        if (this.dataPacketService == s) {
            this.dataPacketService = null;
        }
    }

	void setTopologyService(ITopologyService s) {
        this.topoService = s;
    }

	void unsetTopologyService(ITopologyService s) {
		if (this.topoService == s) {
			this.topoService = null;
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
    	    	
    	// create a thread to act as agent. agentThread()
    	agentReceive = new AgentThreadReceive("agentReceive", 9876);
    	agentReceive.start();

    	agentSend = new AgentSendParent("127.0.0.1", 6789);
        
        // (Topology discoery approach 2) create a thread to send link discovery message periodically
        // !!!!!!!! important !!!!!!!!!!!!!!!!!!!!
        // If you use approach 2, you also need RecA to listen to
        // packet-in message and find the LD message from the
        // Opendaylight platform
    	
    	setChanged();
    	notifyObservers(new Msg(TYPE.TOPO_CHANGE, null));

    	myID = new String("C1");
    }

   
    /**********************************************************************
     * implement this by your self
     *******************************************************************/
    private void abstraction(){
    	System.out.println("Call to abstraction() ; ");
        // compute G-switch by topology
        // use ITopologyManager topoManager
    	// https://developer.cisco.com/media/XNCJavaDocs/org/opendaylight/controller/topologymanager/ITopologyManager.html
    
        System.out.println("Trying to determine local nodes using IConnectionManager");
       
        // TODO : Handle the fact the getLocalNodes() can return null
        // when there is no underlying network.
        
        Set<Node> localNodes = connectionManager.getLocalNodes();
        System.out.println("Printing local edges: ");
        if (localNodes == null)
            System.out.println("     No local edges to print");
        else {      
            Iterator iterLocalEdges = localNodes.iterator();
            while(iterLocalEdges.hasNext())
                System.out.println(iterLocalEdges.next().toString());
        }



        /* ***** Debug Version 1 ***** */
        /* System.out.println("***** Hard Coded Version  *****");
        inNodesMap.clear();
        outNodesMap.clear();
        inNodeConnectorsMap.clear();
        outNodeConnectorsMap.clear(); */


        /* ***** Actual Implementation ******/
        /*
        System.out.println("Actual Implementation ");
    	Name myname = new Name
        
        System.out.println("+++++ Removing previous abstraction.");
        System.out.println(">>>>>>>>>>>>> Clearing inNodesMap");
        inNodesMap.clear();
        System.out.println(">>>>>>>>>>>>> Clearing outNodesMap");
        outNodesMap.clear();
        System.out.println(">>>>>>>>>>>>> Clearing inNodesConnectorMap");
        inNodeConnectorsMap.clear();
        System.out.println(">>>>>>>>>>>>> Clearing outNodesConnectorMap");
        outNodeConnectorsMap.clear();
        System.out.println(">>>>>>>>>>>>> Clearing nb_port");
        nb_ports = 0;

        System.out.println("+++++ Computing the new abstraction : Start");
        Iterator iter_edges;
        
        // In/out edges indexed by Node
        Map<Node,Set<Edge>> nodeEdges = topoManager.getNodeEdges();
        Map<Edge,Set<Property>> edges = topoManager.getEdges();

		System.out.println("*** Domain C1 ***");
        
        for (Map.Entry<Node, Set<Edge>> entry : nodeEdges.entrySet()) { 
             
            System.out.println("**** Itering through the edges of Node : ***** " + entry.getKey());
            
            iter_edges = entry.getValue().iterator();

            while (iter_edges.hasNext()) { 
                Edge edge = (Edge) iter_edges.next();
                Edge inverse = null;

                NodeConnector headConnector = edge.getHeadNodeConnector();
                NodeConnector tailConnector = edge.getTailNodeConnector();

                try { 
                    inverse = new Edge(headConnector, tailConnector);
                } catch (ConstructionException e) {};
                                        
                System.out.println("            ==== Considering edge : " + edge.toString());
                    
                if (!edges.containsKey(inverse)) {
                    System.out.println("Node : " + headConnector.getNode().toString() + "is external to the domain.");
                    System.out.println("OUT");
                    System.out.println("Node : " + tailConnector.getNode().toString() + "will be mapped t a G-Switch port");
                    outNodesMap.put(nb_ports, tailConnector.getNode());
                    outNodeConnectorsMap.put(nb_ports, tailConnector);
                    nb_ports++;    
                }    
            }
        }

        System.out.println("+++++ Computing the new abstraction: End ");

        System.out.println("+++++ Debug map edges ");
        System.out.println(">>>>>>>>>>>>> Printing inNodesMap");
        System.out.println(inNodesMap.toString());
        System.out.println(">>>>>>>>>>>>> Printing outNodesMap");
        System.out.println(outNodesMap.toString());
        System.out.println(">>>>>>>>>>>>> Printing inNodeConnectorsMap");
        System.out.println(inNodeConnectorsMap.toString());
        System.out.println(">>>>>>>>>>>>> Printing outNodeConnectorsMap");
        System.out.println(outNodeConnectorsMap.toString());
        System.out.println(">>>>>>>>>>>>> Printing outNodeConnectorsMap");
        System.out.println(nb_ports);
        */
    }
    
	@Override
	public void edgeOverUtilized(Edge arg0) {
		// TODO Auto-generated method stub
        System.out.println("RecA ======> TOPO UPDATE ========> edgeOverUtilized");
	}

	@Override
	public void edgeUpdate(List<TopoEdgeUpdate> arg0) {
		// sleep to allow NIB to to update before we check the NIB
		try {
			Thread.sleep(5000);
		} catch (Exception ex) {
			Thread.currentThread().interrupt();
		}

		for (TopoEdgeUpdate update : arg0) {
			Edge newEdge = update.getEdge();
			UpdateType updateType = update.getUpdateType();
			Set<Property> edgeProperties = update.getProperty();

			switch (updateType) {
				case ADDED:
					System.out.println("### Edge update is ADDED ###");
					break;
				
				case REMOVED:
					System.out.println("### Edge update is REMOVED ###");
					break;

				case CHANGED:
					System.out.println("### Edge update is CHANGED ###");
					break;

				default:
					break;
			}
		}
		
		// TODO Auto-generated method stub
        System.out.println("RecA ======> TOPO UPDATE ========> edgeUpdate");
        abstraction();
		setChanged();
		notifyObservers(new Msg(TYPE.TOPO_CHANGE, null));
	}

	@Override
	public void edgeUtilBackToNormal(Edge arg0) {
		// TODO Auto-generated method stub
        System.out.println("RecA ======> TOPO UPDATE ========> edgeUtilBackToNormal");
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
