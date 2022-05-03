package p;

public class ACK extends PingPongMessage{
    public ACK(int num) {
        super(num);
        this.seq=num;
    }

    int seq;
}
