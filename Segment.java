package reso.pReseau;

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

    public static long timerD=3;

    int cCount=0;
    static int cMax=3; // maximum d'annulation possible
    void bTimer(SRProtocol p, IPLayer ip) throws Exception
    {
        Segment s=this;
        t=new Timer();
        th=new TimerTask()
        {
            @Override
            public void run()
            {
                try {
                    System.out.println("<Timer expiré pour sq "+s.getSq()+" avec RTO : "+SRProtocol.RTO+ "ms -> "+(SRProtocol.RTO*2)+"ms >");
                    SRProtocol.wSize=1;
                    SRProtocol.RTO=SRProtocol.RTO*2;
                    SRProtocol.slowS=true;
                    //$$
                    // p.sendT(s,ip,true);

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
            t.cancel();
            th.cancel();
            System.out.println("<Timer annulé pour sq "+this.getSq()+"et RTO = "+SRProtocol.RTO+"ms >");

        }
    }
}
