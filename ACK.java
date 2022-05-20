package reso.examples.pReseau;

public class ACK extends PingPongMessage{
    /* Crée un ACK contenant son numéro de séquence
     * @param num numero de séquence de l'ACK
     */

    int seq;
    public ACK(int num) {
        super(num);
        this.seq=num;
    }

    int getSq()
    {
        return seq;
    }
}
