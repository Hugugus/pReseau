/*******************************************************************************
 * Copyright (c) 2011 Bruno Quoitin.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors:
 *     Bruno Quoitin - initial API and implementation
 ******************************************************************************/
package reso.pReseau;

import reso.common.AbstractApplication;
import reso.ip.IPAddress;
import reso.ip.IPHost;
import reso.ip.IPLayer;


public class App
    extends AbstractApplication
{ 
	
	private IPLayer ip;
    private IPAddress dst;
    private  String num="524422526226272777228282822292929";



    public App(IPHost host) {
    	super(host, "sender");
    	this.dst= dst;
        ip= host.getIPLayer();

    }



    public void start()
    throws Exception {
        //ip.addListener(PingPongProtocol.IP_PROTO_PINGPONG, new PingPongProtocol((IPHost) host));
        SRProtocol p = new SRProtocol((IPHost) host);
        ip.addListener(SRProtocol.IP_PROTO_PINGPONG, new SRProtocol((IPHost) host));
        if(host.equals(Demo.host1))
        {
            p.init(ip, num);
        }

        //ip.send(IPAddress.ANY, dst, PingPongProtocol.IP_PROTO_PINGPONG, new PingPongMessage(num));
    }
    
    public void stop() {}
    
}

