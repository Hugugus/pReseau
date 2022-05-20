package reso.examples.pReseau;

import reso.ip.IPLayer;

import java.util.Timer;
import java.util.TimerTask;

public class Segment extends PingPongMessage{
    int seg;
    public Segment(int num,int s)
    {
        super(s);
        this.seg=num;
    }
    int getSeg()
    {
        return seg;
    }

    Timer t;
    TimerTask th;

    boolean received=false;

    SRProtocol sr;

    boolean repeat=false;
    boolean timer=false;

    int cCount=0;
    static int cMax=3; // maximum d'annulation possible
    void bTimer(SRProtocol p, IPLayer ip) throws Exception
    {
        Segment s=this;
        sr=p;
        t=new Timer();
        timer=true;
        th=new TimerTask()
        {
            @Override
            public void run()
            {
                try {
                    if(!received) {
                        repeat=true;
                        SRProtocol.RTO = SRProtocol.RTO * 2;
                        //System.out.println("< Timer expiré "+sr.getTime()+"ms pour sq "+s.getSq()+"  RTO = "+SRProtocol.RTO+"ms >");
                       // System.out.println("<Timer expiré pour sq " + s.getSq() + " avec RTO : " + (SRProtocol.RTO/2) + "ms -> " + SRProtocol.RTO  + "ms >");
                        System.out.println("< Timer expiré ("+sr.getTime()+"ms) pour seg="+s.getSeg()+" et sq "+s.getSq()+"et RTO : "+ (SRProtocol.RTO/2) + "ms -> " + SRProtocol.RTO +"ms >");

                        //System.out.println("<Timer expiré pour sq " + s.getSq() + " avec RTO : " + (SRProtocol.RTO/2) + "ms -> " + SRProtocol.RTO  + "ms >");
                        timer = false;
                        SRProtocol.wSize = 1;
                        //SRProtocol.RTO = SRProtocol.RTO * 2;
                        SRProtocol.slowS = true;
                       // p.sendT(s, ip); // selective repeate
                    }

                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

            }
        };
        t.schedule(th,SRProtocol.RTO);
    }

    void sTimer()
    {
        if(t!=null)
        {
            repeat=false;
            timer=false;
            t.cancel();
            th.cancel();
            //System.out.println("<Timer expiré pour sq " + this.getSq() + " avec RTO : " + SRProtocol.RTO  + " >");
            System.out.println("< Timer annulé ("+sr.getTime()+"ms) pour seg= "+this.getSeg()+" et sq "+this.getSq()+" et RTO = "+SRProtocol.RTO+"ms >");

            //System.out.println("< Timer annulé "+sr.getTime()+"ms pour sq "+this.getSq()+"et RTO = "+SRProtocol.RTO+"ms >");
        }
    }
}
