package org.bouncycastle.math.ec;

import java.math.BigInteger;

/**
 * base class for an elliptic curve
 */
public abstract class ECCurve
{
    ECFieldElement a, b;

    public abstract int getFieldSize();

    public abstract ECFieldElement fromBigInteger(BigInteger x);

    public abstract ECPoint createPoint(BigInteger x, BigInteger y, boolean withCompression);

    public abstract ECPoint decodePoint(byte[] encoded);

    public abstract ECPoint getInfinity();

    public ECFieldElement getA()
    {
        return a;
    }

    public ECFieldElement getB()
    {
        return b;
    }

    /**
     * Elliptic curve over Fp
     */
    public static class Fp extends ECCurve
    {
        BigInteger q;
        ECPoint.Fp infinity;

        public Fp(BigInteger q, BigInteger a, BigInteger b)
        {
            this.q = q;
            this.a = fromBigInteger(a);
            this.b = fromBigInteger(b);
            this.infinity = new ECPoint.Fp(this, null, null);
        }

        public BigInteger getQ()
        {
            return q;
        }

        @Override
        public int getFieldSize()
        {
            return q.bitLength();
        }

        @Override
        public ECFieldElement fromBigInteger(BigInteger x)
        {
            return new ECFieldElement.Fp(this.q, x);
        }

        @Override
        public ECPoint createPoint(BigInteger x, BigInteger y, boolean withCompression)
        {
            return new ECPoint.Fp(this, fromBigInteger(x), fromBigInteger(y), withCompression);
        }

        /**
         * Decode a point on this curve from its ASN.1 encoding. The different
         * encodings are taken account of, including point compression for
         * <code>F<sub>p</sub></code> (X9.62 s 4.2.1 pg 17).
         * @return The decoded point.
         */
        @Override
        public ECPoint decodePoint(byte[] encoded)
        {
            ECPoint p = null;

            switch (encoded[0])
            {
                // infinity
            case 0x00:
                if (encoded.length > 1)
                {
                    throw new RuntimeException("Invalid point encoding");
                }
                p = getInfinity();
                break;
                // compressed
            case 0x02:
            case 0x03:
                int ytilde = encoded[0] & 1;
                byte[]  i = new byte[encoded.length - 1];

                System.arraycopy(encoded, 1, i, 0, i.length);

                ECFieldElement x = new ECFieldElement.Fp(this.q, new BigInteger(1, i));
                ECFieldElement alpha = x.multiply(x.square().add(a)).add(b);
                ECFieldElement beta = alpha.sqrt();

                //
                // if we can't find a sqrt we haven't got a point on the
                // curve - run!
                //
                if (beta == null)
                {
                    throw new RuntimeException("Invalid point compression");
                }

                int bit0 = (beta.toBigInteger().testBit(0) ? 1 : 0);

                if (bit0 == ytilde)
                {
                    p = new ECPoint.Fp(this, x, beta, true);
                }
                else
                {
                    p = new ECPoint.Fp(this, x,
                        new ECFieldElement.Fp(this.q, q.subtract(beta.toBigInteger())), true);
                }
                break;
                // uncompressed
            case 0x04:
                // hybrid
            case 0x06:
            case 0x07:
                byte[]  xEnc = new byte[(encoded.length - 1) / 2];
                byte[]  yEnc = new byte[(encoded.length - 1) / 2];

                System.arraycopy(encoded, 1, xEnc, 0, xEnc.length);
                System.arraycopy(encoded, xEnc.length + 1, yEnc, 0, yEnc.length);

                p = new ECPoint.Fp(this,
                        new ECFieldElement.Fp(this.q, new BigInteger(1, xEnc)),
                        new ECFieldElement.Fp(this.q, new BigInteger(1, yEnc)));
                break;
            default:
                throw new RuntimeException("Invalid point encoding 0x" + Integer.toString(encoded[0], 16));
            }

            return p;
        }

        @Override
        public ECPoint getInfinity()
        {
            return infinity;
        }

        @Override
        public boolean equals(
            Object anObject)
        {
            if (anObject == this)
            {
                return true;
            }

            if (!(anObject instanceof ECCurve.Fp))
            {
                return false;
            }

            ECCurve.Fp other = (ECCurve.Fp) anObject;

            return this.q.equals(other.q)
                    && a.equals(other.a) && b.equals(other.b);
        }

        @Override
        public int hashCode()
        {
            return a.hashCode() ^ b.hashCode() ^ q.hashCode();
        }
    }
}
