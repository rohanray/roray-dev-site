import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class FfmDemoProducer {

    // Layout helpers
    private static final ValueLayout.OfByte BYTE = ValueLayout.JAVA_BYTE;
    private static final ValueLayout LONG_UA_LE_LAYOUT = ValueLayout.JAVA_LONG_UNALIGNED
            .withOrder(ByteOrder.LITTLE_ENDIAN);
    private static final ValueLayout SHORT_UA_LE_LAYOUT = ValueLayout.JAVA_SHORT_UNALIGNED
            .withOrder(ByteOrder.LITTLE_ENDIAN);

    // private static final VarHandle LONG_UA_LE_HANDLE =
    // LONG_UA_LE_LAYOUT.varHandle();
    // private static final VarHandle SHORT_UA_LE_HANDLE =
    // SHORT_UA_LE_LAYOUT.varHandle();

    private static void run(String inputCsvPath, String outputBinPath, int parallel) throws IOException {
        Path inPath = Path.of(inputCsvPath);
        Path out = Path.of(outputBinPath);

        try (FileChannel inCh = FileChannel.open(inPath, StandardOpenOption.READ)) {
            List<Long> offsets = new ArrayList<>();
            // List<Long> nameLengths = new ArrayList<>();
            long totalOutSize = 0;
            int totalRecords = 0;
            // Map file into memory
            try (Arena arena = Arena.ofShared()) {
                // SequenceLayout recordsSeqLayout = MemoryLayout.sequenceLayout(totalRecords,
                // MemoryLayout.structLayout(
                // LONG_UA_LE_LAYOUT.withName("mobile"),
                // SHORT_UA_LE_LAYOUT.withName("age"),
                // SHORT_UA_LE_LAYOUT.withName("name_length"),
                // ValueLayout.JAVA_BOOLEAN.withName("external")

                // ).withName("record")).withName("records");

                // Pass 1 - Determine size

                MemorySegment fileMapSeg = inCh.map(FileChannel.MapMode.READ_ONLY, 0, inCh.size(), arena);
                MemorySegment recMemorySegment = null;

                for (long i = 0; i < fileMapSeg.byteSize(); i++) {
                    byte b = fileMapSeg.get(BYTE, i);
                    if (b == 10) {
                        // recMemorySegment = arena.allocate(inFileSize - i + 1);
                        recMemorySegment = fileMapSeg.asSlice(i);
                        break;
                    }
                }

                if (recMemorySegment != null) {
                    int currNameLength = 0;
                    boolean firstComma = true;
                    for (long i = 0; i < recMemorySegment.byteSize(); i++) {
                        switch (recMemorySegment.get(BYTE, i)) {
                            case 10 -> {
                                offsets.add(i + 1); // 10 is new line, so increment offset
                                totalOutSize = totalOutSize + currNameLength + Long.BYTES + Short.BYTES + Short.BYTES
                                        + Byte.BYTES; // 1 for mobile, 2 for age, 2 for name_length, 1 for external
                                                      // field flag
                                currNameLength = 0;
                                firstComma = true;
                                break;
                            }
                            case 44 -> {
                                firstComma = false;
                                break;
                            }
                            default -> {
                                if (!firstComma) {
                                    currNameLength++;
                                }
                            }
                        }

                    }

                    totalRecords = offsets.size();
                    System.out.println("Total in file byte size / recMemorySegment: ");
                    System.out.println(recMemorySegment.byteSize());
                    System.out.println("Total Records: " + totalRecords);
                    System.out.println("Total Out Bin Size: " + totalOutSize);

                    // Pass 2 - Write to memory segment in binary

                    GroupLayout recordLayout = MemoryLayout.structLayout(
                            LONG_UA_LE_LAYOUT.withName("mobile"),
                            SHORT_UA_LE_LAYOUT.withName("age"),
                            SHORT_UA_LE_LAYOUT.withName("name_length"),
                            ValueLayout.JAVA_BOOLEAN.withName("external")).withName("record");

                    // Get var handlesx for fields
                    VarHandle VH_MOBILE = recordLayout.varHandle(PathElement.groupElement("mobile"));
                    VarHandle VH_AGE = recordLayout.varHandle(PathElement.groupElement("age"));
                    VarHandle VH_NAME_LENGTH = recordLayout.varHandle(PathElement.groupElement("name_length"));
                    VarHandle VH_EXTERNAL = recordLayout.varHandle(PathElement.groupElement("external"));

                    MemorySegment outBinSegment = Arena.ofShared().allocate(totalOutSize);
                    // long currBinSegOffset = 0;
                    // for (int i = 0; i < totalRecords; i++) {
                    // long offset = offsets.get(i);
                    // System.out.println("Offset at " + i + " : " + offset);
                    // System.out.println("Offsets: " + new String(new byte[] {
                    // recMemorySegment.get(BYTE, offset) },
                    // StandardCharsets.UTF_8));
                    // }

                    // START: short writer
                    long allNamesLength = 0;
                    for (int i = 0; i < totalRecords; i++) {
                        // System.out.println("Inside Writer");
                        // System.out.println("Offset at " + i + " : " + offsets.get(i));
                        for (int j = 0;; j++) {
                            if (44 == recMemorySegment.get(BYTE, offsets.get(i) + j)) {
                                // System.out.println("Writer j: " + j);
                                MemorySegment currRecMS = outBinSegment
                                        .asSlice((i * recordLayout.byteSize()) + allNamesLength, recordLayout);

                                VH_MOBILE.set(currRecMS, 0L, parseLong(recMemorySegment, offsets.get(i) + j + 4,
                                        offsets.get(i) + j + 14));

                                VH_AGE.set(currRecMS, 0L,
                                        parseShort(recMemorySegment, offsets.get(i) + j + 1, offsets.get(i) + j + 3));

                                VH_NAME_LENGTH.set(currRecMS, 0L, (short) j);

                                // VH_EXTERNAL.set(currRecMS, 0L, true);

                                outBinSegment
                                        .asSlice(((i + 1) * recordLayout.byteSize())
                                                + allNamesLength, j)
                                        .copyFrom(recMemorySegment.asSlice(offsets.get(i), j));

                                // if (i == 0) {
                                //     System.out.println("Special case for record 1");
                                //     char fnc = (char) recMemorySegment
                                //             .asSlice(offsets.get(i), j)
                                //             .get(BYTE, j - 1);
                                //     System.out.println("FNC IN: " + fnc);

                                //     fnc = (char) outBinSegment
                                //             .asSlice(((i + 1) * recordLayout.byteSize()) + allNamesLength, j)
                                //             .get(BYTE, j - 1);
                                //     System.out.println("FNC OUT: " + fnc);

                                // }

                                allNamesLength = allNamesLength + j;
                                break;
                            }
                        }
                    }

                    // END: short writer

                    // START : TEST BIN MS reader
                    System.out.println("Reading from binary memory segment:");
                    long readAllNamesLength = 0;
                    for (int i = 0; i < totalRecords; i++) {
                        System.out.println("--- Record " + i + " ---");
                        MemorySegment currReadMS = outBinSegment
                                .asSlice((i * recordLayout.byteSize()) + readAllNamesLength, recordLayout);
                        long _mobile = (long) VH_MOBILE.get(currReadMS, 0L);
                        System.out.println("Mobile: " + _mobile);
                        short _age = (short) VH_AGE.get(currReadMS, 0L);
                        System.out.println("Age: " + _age);
                        short _name_length = (short) VH_NAME_LENGTH.get(currReadMS, 0L);
                        System.out.println("Name Length: " + _name_length);
                        boolean _isExt = (boolean) VH_EXTERNAL.get(currReadMS, 0L);
                        System.out.println("Is External: " + _isExt);
                        String name = outBinSegment
                                .asSlice((i * recordLayout.byteSize()) + readAllNamesLength, _name_length)
                                .getString(0, StandardCharsets.UTF_8);
                        System.out.println("Name: " + name);


                         if (i == 2) {
                            System.out.println("Special case for record 1");
                            char fnc = (char) outBinSegment
                                    .asSlice(((i + 1) * recordLayout.byteSize()) + readAllNamesLength, _name_length)
                                    .get(BYTE, _name_length-1);
                            System.out.println("FNC OUT 1: " + fnc);
                        }


                        readAllNamesLength += _name_length;

                       
                    }

                    // END : TEST BIN MS reader
                }
            }
        }

    }

    public static void main(String[] args) throws Exception {
        System.out.println(args[0]);
        int parallelism = (args.length > 2) ? Integer.parseInt(args[2])
                : Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        try {
            run(args[0], args[1], parallelism);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static Long parseLong(MemorySegment seg, long start, long end) {
        long value = 0L;
        for (long i = start; i < end; i++) {
            byte b = seg.get(BYTE, i);
            value = value * 10 + (b - '0');
        }
        return value;
    }

    static Short parseShort(MemorySegment seg, long start, long end) {
        short value = 0;
        for (long i = start; i < end; i++) {
            byte b = seg.get(BYTE, i);
            value = (short) (value * 10 + (b - '0'));
        }
        return value;
    }
}