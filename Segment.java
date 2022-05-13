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

    public static int timerD=2;

    int cCount=0;
    int cMax=3; // maximum d'annulation possible
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
                    System.out.println("\n Message renvoyé \n");
                    p.sendT(s,ip,true);

                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

            }
        };
        t.schedule(th,timerD*1000);
    }

    void sTimer()
    {
        if(t!=null)
        {
            t.cancel();
            th.cancel();
            System.out.println(" Timer annulé pour "+this);
        }
    }
}
