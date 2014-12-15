package reca;


import java.util.Hashtable;
import java.util.Dictionary;

import org.apache.felix.dm.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.opendaylight.controller.sal.core.ComponentActivatorAbstractBase;
import org.opendaylight.controller.sal.topology.IListenTopoUpdates;
import org.opendaylight.controller.sal.packet.IDataPacketService;
import org.opendaylight.controller.sal.routing.IRouting;
import org.opendaylight.controller.sal.flowprogrammer.IFlowProgrammerService;
import org.opendaylight.controller.sal.topology.ITopologyService;
import org.opendaylight.controller.switchmanager.ISwitchManager;
import org.opendaylight.controller.topologymanager.ITopologyManager;
import org.opendaylight.controller.sal.connection.IPluginOutConnectionService;

public class Activator extends ComponentActivatorAbstractBase {
    protected static final Logger logger = LoggerFactory
            .getLogger(Activator.class);

    /**
     * Function called when the activator starts just after some
     * initializations are done by the
     * ComponentActivatorAbstractBase.
     *
     */
    public void init() {

    }

    /**
     * Function called when the activator stops just before the
     * cleanup done by ComponentActivatorAbstractBase
     *
     */
    public void destroy() {

    }

    /**
     * Function that is used to communicate to dependency manager the
     * list of known implementations for services inside a container
     *
     *
     * @return An array containing all the CLASS objects that will be
     * instantiated in order to get an fully working implementation
     * Object
     */
    public Object[] getImplementations() {
        Object[] res = { reca.class };
        return res;
    }

    /**
     * Function that is called when configuration of the dependencies
     * is required.
     *
     * @param c dependency manager Component object, used for
     * configuring the dependencies exported and imported
     * @param imp Implementation class that is being configured,
     * needed as long as the same routine can configure multiple
     * implementations
     * @param containerName The containerName being configured, this allow
     * also optional per-container different behavior if needed, usually
     * should not be the case though.
     */
    public void configureInstance(Component c, Object imp, String containerName) {
        if (imp.equals(reca.class)) {
            // export the services
            Dictionary<String, String> props = new Hashtable<String, String>();
            props.put("salListenerName", "reca");
            c.setInterface(new String[] { IListenTopoUpdates.class.getName() }, props);

            // register dependent modules
            c.add(createContainerServiceDependency(containerName).setService(
                    ISwitchManager.class).setCallbacks("setSwitchManager",
                    "unsetSwitchManager").setRequired(true));

            c.add(createContainerServiceDependency(containerName).setService(
                    ITopologyManager.class).setCallbacks("setTopologyManager",
                    "unsetTopologyManager").setRequired(true));

            c.add(createContainerServiceDependency(containerName).setService(
                    IDataPacketService.class).setCallbacks(
                    "setDataPacketService", "unsetDataPacketService")
                    .setRequired(true));
<<<<<<< HEAD
=======
			
<<<<<<< HEAD
			c.add(createContainerServiceDependency(containerName).setService(
                    IPluginOutConnectionService.class).setCallbacks(
                    "setDomainChecker", "unsetDomainChecker")
                    .setRequired(true));
>>>>>>> 373626c99e082036ccfeb792595aaeffadaea2a2

=======
>>>>>>> 2429ba9de9d29e40dff976b3d22fea37964cd2ba
            c.add(createContainerServiceDependency(containerName).setService(
                    IFlowProgrammerService.class).setCallbacks(
                    "setFlowProgrammerService", "unsetFlowProgrammerService")
                    .setRequired(true));
<<<<<<< HEAD
=======

			c.add(createContainerServiceDependency(containerName).setService(
                    ITopologyService.class).setCallbacks(
                    "setTopologyService", "unsetTopologyservice")
                    .setRequired(true));
>>>>>>> 373626c99e082036ccfeb792595aaeffadaea2a2
            
            c.add(createContainerServiceDependency(containerName).setService(
                    IRouting.class).setCallbacks("setRouting",
                    "unsetRouting").setRequired(true));
        }
    }
}