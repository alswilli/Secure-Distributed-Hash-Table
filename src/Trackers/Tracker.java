package Trackers;

import API.ChordTracker;
import Trackers.IDA.IDA;
import Trackers.Partitions.Partition;
import Trackers.Partitions.SealedPartition;
import org.apache.commons.lang3.SerializationUtils;

import javax.crypto.Cipher;
import javax.crypto.SealedObject;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.Serializable;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;


public class Tracker<RESOURCE_TYPE extends Serializable> implements ChordTracker {

    private static final byte[] initVector = "RandomInitVector".getBytes(StandardCharsets.UTF_8);
    private static final byte[] key = "RandomInitKeyVec".getBytes(StandardCharsets.UTF_8);
    private static final String ENCRYPTION_ALGORITHM = "AES/CBC/PKCS5PADDING";
    private static final int MAX_PARTITIONS = 5;
    private static final int MIN_PARTITIONS = 3;
    private static final int PADDING = 10;

    private SecretKeySpec keySpec;
    private IvParameterSpec iv;
    private Cipher encryptCipher;
    private Cipher decryptCipher;

    private ChordCache cache;
    private IDA ida;

    Tracker () {
        cache = new ChordCache();
        ida = new IDA(MAX_PARTITIONS, MIN_PARTITIONS, PADDING);

        keySpec = new SecretKeySpec(key, "AES");
        iv = new IvParameterSpec(initVector);

        encryptCipher = getCipher();
        decryptCipher = getCipher();
    }

    private Cipher getCipher() {
        try {
            return Cipher.getInstance(ENCRYPTION_ALGORITHM);
        } catch(Exception e) {
            throw new RuntimeException();
        }
    }

    public Integer assignId() {
        return -1;
    }

    //https://www.geeksforgeeks.org/sha-1-hash-in-java/
    public static int encryptThisString(String input) {
        try {

            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] messageDigest = md.digest(input.getBytes());
            BigInteger no = new BigInteger(1, messageDigest);
            String hashtext = no.toString(16);
            while (hashtext.length() < 32) {
                hashtext = "0" + hashtext;
            }

            BigInteger key = new BigInteger(hashtext, 16);
            int keyId = key.mod(new BigInteger("256", 10)).intValue();
            return keyId;
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /* Insert
    *
    * Gets selected node from cache cache
    *
    * Takes a resource and the assigned resource key from the node as input
    *
    * Partition is the IDA, assignID portions
    *
    * List will have partitionID, partition
    *
    * - selected = ChordCache.pop()
    * - list = this.partition(resource)
    * - keymap.put for each element in list
    * - selected.insert-> (for all elements in list)    *
    *
    *
    *
    * */

    /* Partition
    *
    * Takes in the resource
    *
    * Calls IDA on resource (which also does all the encoding work)
    *
    * - listOfPartitions = IDA(resource)
    * - return listOfPartitions
    *
    *
    *
    * */

    private List<SealedPartition> partitionResource (RESOURCE_TYPE resource) {
        byte[] serialized = this.serialize(resource);
        return sealPartitions(ida.encodeBytes(serialized));
    }

    private RESOURCE_TYPE reassemblePartition (List<SealedPartition> partitionList) {
        byte[] resourceByteStream = ida.getDecodedBytes(unsealPartitions(partitionList));
        return SerializationUtils.deserialize(resourceByteStream);
    }

    private List<SealedPartition> sealPartitions(List<Partition> partitions) {
        List<SealedPartition> sealedPartitions = new ArrayList<>();

        for (Partition p : partitions) {
            try {
                encryptCipher.init(Cipher.ENCRYPT_MODE, keySpec, iv);
                SealedObject ob = new SealedObject(p, encryptCipher);

                SealedPartition sp = new SealedPartition(p.getKey(), ob);
                sealedPartitions.add(sp);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return sealedPartitions;
    }

    private List<Partition> unsealPartitions(List<SealedPartition> sealedPartitions) {
        List<Partition> partitions = new ArrayList<>();

        for (SealedPartition sp: sealedPartitions) {
            try {

                decryptCipher.init(Cipher.DECRYPT_MODE, keySpec, iv);
                Partition p = (Partition) sp.getObject(decryptCipher);
                partitions.add(p);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return partitions;
    }

    private byte[] serialize (RESOURCE_TYPE resource) {
        byte[] resourceBytes = SerializationUtils.serialize(resource);
        byte[] augmentedBytes = new byte[resourceBytes.length + PADDING];
        System.arraycopy(resourceBytes, 0, augmentedBytes, 0, resourceBytes.length);

        return augmentedBytes;
    }

    public static void main(String[] args) {

        People roy = new People("Roy", "Shadmon", "12345");
        Tracker<People> tracker = new Tracker<>();

        List<SealedPartition> sp = tracker.partitionResource(roy);

        roy = tracker.reassemblePartition(sp);

        System.out.println(roy.firstname);
    }
  
    /* Lookup
    *
    * Gets selected node from the cache cache
    * Takes in a key from the requesting AbstractNode
    * Tracker finds key in hash table (keymap)
    * For each key, query -> gets the partitions -> adds each partition and its key to a list
    * Call inverse on list
    * Returns resource to node (encryted? PKC?)
    *
    * - selected = ChordCache.pop()
    * - listOfPartitionKeys = keymap.get(resourceKey)
    * - listOfPartitions = query (for each partition key)
    * - resource = inverse(listOfPartitons)
    * - return resource
    *
    * */

}

class People implements Serializable {

    String firstname;
    String lastname;
    String password;

    People(String firstname, String lastname, String password){
        this.firstname = firstname;
        this.lastname = lastname;
        this.password = password;
    }

}