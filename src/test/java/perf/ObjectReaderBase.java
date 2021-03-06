package perf;

import java.io.*;

import com.fasterxml.jackson.databind.*;

abstract class ObjectReaderBase
{
    protected final static int WARMUP_ROUNDS = 5;

    protected String _desc1, _desc2;
    
    protected int hash;

    protected int roundsDone = 0;

    protected int REPS;
    
    protected <T1, T2> void testFromBytes(ObjectMapper mapper1, String desc1,
            T1 inputValue1, Class<T1> inputClass1,
            ObjectMapper mapper2, String desc2,
            T2 inputValue2, Class<T2> inputClass2)
        throws Exception
    {
        final byte[] byteInput1 = mapper1.writeValueAsBytes(inputValue1);
        final byte[] byteInput2 = mapper2.writeValueAsBytes(inputValue2);
        // Let's try to guestimate suitable size... to get to N megs to process
        REPS = (int) ((double) (8 * 1000 * 1000) / (double) byteInput1.length);

        // sanity check:
        /*T1 back1 =*/ mapper1.readValue(byteInput1, inputClass1);
        /*T2 back2 =*/ mapper2.readValue(byteInput2, inputClass2);
        System.out.println("Input successfully round-tripped for both styles...");

        _desc1 = String.format("%s (%d bytes)", desc1, byteInput1.length);
        _desc2 = String.format("%s (%d bytes)", desc2, byteInput2.length);
        
        doTest(mapper1, byteInput1, inputClass1, mapper2, byteInput2, inputClass2);
    }
    
    protected <T1, T2> void testFromString(ObjectMapper mapper1, String desc1,
            T1 inputValue1, Class<T1> inputClass1,
            ObjectMapper mapper2, String desc2, T2 inputValue2, Class<T2> inputClass2)
        throws Exception
    {
        final String input1 = mapper1.writeValueAsString(inputValue1);
        final String input2 = mapper2.writeValueAsString(inputValue2);
        // Let's try to guestimate suitable size... to get to N megs to process
        REPS = (int) ((double) (8 * 1000 * 1000) / (double) input1.length());
        _desc1 = String.format("%s (%d chars)", desc1, input1.length());
        _desc2 = String.format("%s (%d chars)", desc2, input2.length());

        // sanity check:
        /*T1 back1 =*/ mapper1.readValue(input1, inputClass1);
        /*T2 back2 =*/ mapper2.readValue(input2, inputClass2);
        System.out.println("Input successfully round-tripped for both styles...");
        
        doTest(mapper1, input1, inputClass1, mapper2, input2, inputClass2);
    }
    
    protected void doTest(ObjectMapper mapper1, byte[] byteInput1, Class<?> inputClass1,
            ObjectMapper mapper2, byte[] byteInput2, Class<?> inputClass2)
        throws Exception
    {
        System.out.printf("Read %d bytes to bind (%d as array); will do %d repetitions\n",
                byteInput1.length, byteInput2.length, REPS);

        final ObjectReader jsonReader = mapper1.reader()
                .with(DeserializationFeature.EAGER_DESERIALIZER_FETCH)
                .withType(inputClass1);
        final ObjectReader arrayReader = mapper2.reader()
                .with(DeserializationFeature.EAGER_DESERIALIZER_FETCH)
                .withType(inputClass2);
        
        int i = 0;
        final int TYPES = 2;

        final long[] times = new long[TYPES];
        while (true) {
            Thread.sleep(100L);
            int type = (i++ % TYPES);

            String msg;
            long msecs;
            
            switch (type) {
            case 0:
                msg = _desc1;
                msecs = testDeser1(REPS, byteInput1, jsonReader);
                break;
            case 1:
                msg = _desc2;
                msecs = testDeser2(REPS, byteInput2, arrayReader);
                break;
            default:
                throw new Error();
            }
            updateStats(type, (i % 17) == 0, msg, msecs, times);
        }
    }

    protected void doTest(ObjectMapper mapper1, String input1, Class<?> inputClass1,
            ObjectMapper mapper2, String input2, Class<?> inputClass2)
        throws Exception
    {
        System.out.printf("Read %d bytes to bind (%d as array); will do %d repetitions\n",
                input1.length(), input2.length(), REPS);

        final ObjectReader jsonReader = mapper1.reader()
                .with(DeserializationFeature.EAGER_DESERIALIZER_FETCH)
                .withType(inputClass1);
        final ObjectReader arrayReader = mapper2.reader()
                .with(DeserializationFeature.EAGER_DESERIALIZER_FETCH)
                .withType(inputClass2);
        
        int i = 0;
        final int TYPES = 2;

        final long[] times = new long[TYPES];
        while (true) {
            Thread.sleep(100L);
            int type = (i++ % TYPES);

            String msg;
            long msecs;
            
            switch (type) {
            case 0:
                msg = _desc1;
                msecs = testDeser1(REPS, input1, jsonReader);
                break;
            case 1:
                msg = _desc2;
                msecs = testDeser2(REPS, input2, arrayReader);
                break;
            default:
                throw new Error();
            }
            updateStats(type, (i % 17) == 0, msg, msecs, times);
        }
    }
    
    private void updateStats(int type, boolean doGc, String msg, long msecs, long[] times)
        throws Exception
    {
        // skip first N rounds to let results stabilize
        if (roundsDone >= WARMUP_ROUNDS) {
            times[type] += msecs;
        }
        System.out.printf("Test '%s' [hash: 0x%s] -> %d msecs\n", msg, this.hash, msecs);
        if (type == 0) {
            ++roundsDone;
            if ((roundsDone % 3) == 0 && roundsDone > WARMUP_ROUNDS) {
                double den = (double) (roundsDone - WARMUP_ROUNDS);
                System.out.printf("Averages after %d rounds (Object / Array): %.1f / %.1f msecs\n",
                        (int) den,
                        times[0] / den, times[1] / den);
                        
            }
            System.out.println();
        }
        if (doGc) {
            System.out.println("[GC]");
            Thread.sleep(100L);
            System.gc();
            Thread.sleep(100L);
        }
    }

    protected long testDeser1(int reps, byte[] input, ObjectReader reader) throws Exception {
        return _testDeser(reps, input, reader);
    }
    protected long testDeser2(int reps, byte[] input, ObjectReader reader) throws Exception {
        return _testDeser(reps, input, reader);
    }
    
    protected final long _testDeser(int reps, byte[] input, ObjectReader reader) throws Exception
    {
        long start = System.currentTimeMillis();
        Object result = null;
        while (--reps >= 0) {
            result = reader.readValue(input);
        }
        hash = result.hashCode();
        return System.currentTimeMillis() - start;
    }

    protected long testDeser1(int reps, String input, ObjectReader reader) throws Exception {
        return _testDeser(reps, input, reader);
    }

    protected long testDeser2(int reps, String input, ObjectReader reader) throws Exception {
        return _testDeser(reps, input, reader);
    }
    
    protected final long _testDeser(int reps, String input, ObjectReader reader) throws Exception
    {
        long start = System.currentTimeMillis();
        Object result = null;
        while (--reps >= 0) {
            result = reader.readValue(input);
        }
        hash = result.hashCode();
        return System.currentTimeMillis() - start;
    }

    public static byte[] readAll(String filename) throws IOException
    {
        File f = new File(filename);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream((int) f.length());
        byte[] buffer = new byte[4000];
        int count;
        FileInputStream in = new FileInputStream(f);
        
        while ((count = in.read(buffer)) > 0) {
            bytes.write(buffer, 0, count);
        }
        in.close();
        return bytes.toByteArray();
    }
}
