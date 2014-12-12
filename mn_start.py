#!/usr/bin/python

"""
Create a network where different switches are connected to
different controllers, by creating a custom Switch() subclass.
"""

from mininet.net import Mininet
from mininet.node import OVSSwitch, Controller, RemoteController
from mininet.topolib import TreeTopo
from mininet.topo import LinearTopo
from mininet.log import setLogLevel
from mininet.cli import CLI

setLogLevel( 'info' )

# Two local and one "external" controller (which is actually c0)
# Ignore the warning message that the remote isn't (yet) running
c0 = Controller( 'c0', ip='127.0.0.1' )
c2 = RemoteController( 'c2', ip='129.236.231.133' )

cmap = { 's1': c0, 's2': c2 }

class MultiSwitch( OVSSwitch ):
    "Custom Switch() subclass that connects to different controllers"
    def start( self, controllers ):
        return OVSSwitch.start( self, [ cmap[ self.name ] ] )

topo = LinearTopo( k=2, n=1 )
net = Mininet( topo=topo, switch=MultiSwitch, build=False )
for c in [ c0, c2 ]:
    net.addController(c)
net.build()
net.start()
CLI( net )
net.stop()
