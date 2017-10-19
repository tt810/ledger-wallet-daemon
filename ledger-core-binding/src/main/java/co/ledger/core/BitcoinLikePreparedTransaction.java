// AUTOGENERATED FILE - DO NOT MODIFY!
// This file generated by Djinni from bitcoin_like_wallet.djinni

package co.ledger.core;

import java.util.ArrayList;

public final class BitcoinLikePreparedTransaction {


    /*package*/ final int version;

    /*package*/ final ArrayList<BitcoinLikeOutput> inputs;

    /*package*/ final ArrayList<String> paths;

    /*package*/ final ArrayList<BitcoinLikeOutput> outputs;

    /*package*/ final int lockTime;

    public BitcoinLikePreparedTransaction(
            int version,
            ArrayList<BitcoinLikeOutput> inputs,
            ArrayList<String> paths,
            ArrayList<BitcoinLikeOutput> outputs,
            int lockTime) {
        this.version = version;
        this.inputs = inputs;
        this.paths = paths;
        this.outputs = outputs;
        this.lockTime = lockTime;
    }

    public int getVersion() {
        return version;
    }

    public ArrayList<BitcoinLikeOutput> getInputs() {
        return inputs;
    }

    public ArrayList<String> getPaths() {
        return paths;
    }

    public ArrayList<BitcoinLikeOutput> getOutputs() {
        return outputs;
    }

    public int getLockTime() {
        return lockTime;
    }

    @Override
    public String toString() {
        return "BitcoinLikePreparedTransaction{" +
                "version=" + version +
                "," + "inputs=" + inputs +
                "," + "paths=" + paths +
                "," + "outputs=" + outputs +
                "," + "lockTime=" + lockTime +
        "}";
    }

}
