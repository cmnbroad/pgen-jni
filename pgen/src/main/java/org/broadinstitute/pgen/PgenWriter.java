/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package org.broadinstitute.pgen;

import htsjdk.io.HtsPath;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
//import htsjdk.variant.vcf.VCFConstants;
import htsjdk.variant.vcf.VCFHeader;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class PgenWriter implements VariantContextWriter {

    public static final int NO_CALL_VALUE = -9;
    private final long pgenContextHandle;
    private ByteBuffer alleleBuffer;

    static {
        System.loadLibrary("pgen");
    }

    // doesn't support phasing (phasing ?)
    // dosage - ?? fraction of sample that is expressing that allele ?)
    //
    // needs to know the number of variants and samples
    public PgenWriter(HtsPath file, long numberOfVariants, int numberOfSamples){
        pgenContextHandle = createPgenMetadata();
        alleleBuffer = createBuffer(numberOfSamples*2*4); //samples * ploidy * bytes in int32
        alleleBuffer.order(ByteOrder.LITTLE_ENDIAN);

        if(openPgen(file.getRawInputString(), numberOfVariants, numberOfSamples, pgenContextHandle ) != 0){
            throw new RuntimeException("failed");
        }
    }

    @Override
    public void writeHeader(final VCFHeader header) {
       // throw new UnsupportedOperationException("PGEN files don't support an independant header write.");
    }


    @Override
    public void close() {
        closePgen(pgenContextHandle);
        destroyByteBuffer(alleleBuffer);
        alleleBuffer = null;
    }

    @Override
    public boolean checkError() {
        return false;
    }

    private static Map<Allele, Integer> buildAlleleMap(final VariantContext vc) {
        final Map<Allele, Integer> alleleMap = new HashMap<>(vc.getAlleles().size() + 1);
        alleleMap.put(Allele.NO_CALL, NO_CALL_VALUE); // convenience for lookup
        final List<Allele> alleles = vc.getAlleles();
        for (int i = 0; i < alleles.size(); i++) {
            alleleMap.put(alleles.get(i), i);
        }

        return alleleMap;
    }


    @Override
    public void add(final VariantContext vc) {
        //reset buffer
        alleleBuffer.clear();
        final Map<Allele, Integer> alleleMap = buildAlleleMap(vc);
        for (final Genotype g : vc.getGenotypes()) {
            if (g.getPloidy() != 2) {
                throw new PgenJniException("PGEN only supports diploid samples and we see one with ploidy = " + g.getPloidy()
                        + " at line " + vc.toStringDecodeGenotypes());
            }
            for (final Allele allele : g.getAlleles()) {
                final Integer mapping = alleleMap.get(allele);
                try {
                    alleleBuffer.putInt(mapping);
                } catch (Exception e){
                    throw new RuntimeException("error while adding value: " + mapping +" for  Allele: " + allele.toString() + " from Genotype: " + g.toString() + " at buffer position: "+ alleleBuffer.position());
                }
            }
        }
        if (alleleBuffer.position() != alleleBuffer.limit()) {
            throw new IllegalStateException("Allele buffer is not completely filled, we have a problem. " +
                    "Position: " + alleleBuffer.position() + " Expected " + alleleBuffer.limit());
        }
        alleleBuffer.rewind();
        appendAlleles(pgenContextHandle, alleleBuffer);
    }

    @Override
    public void setHeader(final VCFHeader header) {

    }

    private static native long createPgenMetadata();
    private static native int openPgen(String file, long numberOfVariants, long numberOfSamples, long pgenContextHandle);
//    private static native void appendBiallelic(long pgenContextHandle, )
    private native void closePgen(long pgenContextHandle);
    private native void appendAlleles(long pgenContextHandle, ByteBuffer alleles);

    private static native ByteBuffer createBuffer(int length);
    private static native void destroyByteBuffer(ByteBuffer buffer);
}
