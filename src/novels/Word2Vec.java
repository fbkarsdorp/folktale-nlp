package novels;

import java.io.*;
import java.util.HashMap;

public class Word2Vec {

    private static final int MAX_SIZE = 50;
    public HashMap<String, float[]> wordMap = new HashMap<String, float[]>();

    public Word2Vec(String path) throws IOException {
        DataInputStream dataInputStream = null;
        BufferedInputStream bufferedInputStream = null;
        double len = 0;
        float vector = 0;
        int words, size;
        try {
            bufferedInputStream = new BufferedInputStream(new FileInputStream(path));
            dataInputStream = new DataInputStream(bufferedInputStream);
            words = Integer.parseInt(readString(dataInputStream));
            size = Integer.parseInt(readString(dataInputStream));
            String word;
            float[] vectors = null;
            for (int i = 0; i < words; i++) {
                word = readString(dataInputStream);
                vectors = new float[size];
                len = 0;
                for (int j = 0; j < size; j++) {
                    vector = readFloat(dataInputStream);
                    len += vector * vector;
                    vectors[j] = (float) vector;
                }
                len = Math.sqrt(len);
                for (int j = 0; j < size; j++) {
                    vectors[j] /= len;
                }
                wordMap.put(word, vectors);
                dataInputStream.read();
            }
        } finally {
            bufferedInputStream.close();
            dataInputStream.close();
        }
    }

    private static String readString(DataInputStream dataInputStream) throws IOException {
        byte[] bytes = new byte[MAX_SIZE];
        byte b = dataInputStream.readByte();
        int i = -1;
        StringBuilder sb = new StringBuilder();
        while (b != 32 && b != 10) {
            i++;
            bytes[i] = b;
            b = dataInputStream.readByte();
            if (i == 49) {
                sb.append(new String(bytes));
                i = -1;
                bytes = new byte[MAX_SIZE];
            }
        }
        sb.append(new String(bytes, 0, i + 1));
        return sb.toString();
    }

    public static float readFloat(InputStream is) throws IOException {
        byte[] bytes = new byte[4];
        is.read(bytes);
        return getFloat(bytes);
    }

    public static float getFloat(byte[] b) {
        int accum = 0;
        accum = accum | (b[0] & 0xff) << 0;
        accum = accum | (b[1] & 0xff) << 8;
        accum = accum | (b[2] & 0xff) << 16;
        accum = accum | (b[3] & 0xff) << 24;
        return Float.intBitsToFloat(accum);
    }

    public double similarity(String wordA, String wordB) {
        float[] vectorA = wordMap.get(wordA);
        float[] vectorB = wordMap.get(wordB);
        double dist = 0;
        for (int i = 0; i < vectorA.length; i++) {
            dist += vectorA[i] * vectorB[i];
        }
        return dist;
    }

    public static void main(String[] args) throws IOException {
        Word2Vec model = new Word2Vec("files/vectors.bin");
        System.out.println(model.similarity("king", "queen"));
    }

}
