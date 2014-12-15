#!/usr/bin/python

"""
This example creates a multi-controller network from semi-scratch by
using the net.add*() API and manually starting the switches and
controllers.
This is the "mid-level" API, which is an alternative to the "high-level"
Topo() API which supports parametrized topology classes.
Note that one could also create a custom switch class and pass it into
the Mininet() constructor.
"""

from mininet.net import Mininet
from mininet.node import OVSSwitch
from mininet.cli import CLI
from mininet.log import setLogLevel
from mininet.node import RemoteController

def multiControllerNet():
    "Create a network from semi-scratch with multiple controllers."
    
    setLogLevel( 'info' )
    
    net = Mininet( switch=OVSSwitch )
    

    print "*** Creating controllers"
    c1 = net.addController( 'c1', controller=RemoteController, ip='127.0.0.1' )
    c2 = net.addController( 'c2', controller=RemoteController, ip='160.39.130.141' )
    # c3 = net.addController( 'c3', controller=RemoteController, ip='127.0.0.1' )
    
    print "*** Creating switches"
    s1 = net.addSwitch( 's1' )
    # s2 = net.addSwitch( 's2' )
    s3 = net.addSwitch( 's3' )
    # s4 = net.addSwitch( 's4' )
    # s5 = net.addSwitch( 's5' )
    
    print "*** Creating hosts"
#    h1 = net.addHost( 'h1' )
    # h2 = net.addHost( 'h2' )
    # h3 = net.addHost( 'h3' )
    # h4 = net.addHost( 'h4' )
    
    print "*** Creating links"
    # add one host per switch
#    net.addLink( s1, h1 )
    # net.addLink( s2, h2 )
    # net.addLink( s3, h3 )
    # net.addLink( s4, h4 )
    # net.addLink( s5, h5 )

    # link s2 and s3
    net.addLink( s1, s3 )
    #net.addLink( c2, c3 )
    #net.addLink( c1, c3 )

    print "*** Starting network"
    net.build()
    c1.start()
    c2.start()
    s1.start( [ c1 ] )
    #s2.start( [ c1 ] )
    s3.start( [ c2 ] )
    #s4.start( [ c2 ] )
    #s5.start( [ c3 ] )
    
    print "*** Testing network"
    #net.pingAll()

    print "*** Running CLI"
    CLI( net )

    print "*** Stopping network"
    #net.stop()

if __name__ == '__main__':
    setLogLevel( 'info' ) # for CLI output
    multiControllerNet()
