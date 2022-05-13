package p;

import reso.ip.IPAddress;
import reso.ip.IPLayer;

import java.util.Timer;
import java.util.TimerTask;

public class Segment extends PingPongMessage{
    int sN=0;
    public Segment(int num,int s) {
        super(num);
        this.sN=s;
    }
    int getS()
    {
        return sN;
    }

    Timer t;
    TimerTask th;

    public static int timerD=2;

    int cCount=0;
    int cMax=3;
    void bTimer(PingPongProtocol p,IPLayer ip) throws Exception
    {
        Segment s=this;
        t=new Timer();
        th=new TimerTask()
        {
            @Override
            public void run()
            {
                try {
                    cCount++;
                    if(cCount<=cMax)
                    { p.sendT(ip,false);}
                    else
                    {
                        t.cancel();
                        th.cancel();
                    }

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
