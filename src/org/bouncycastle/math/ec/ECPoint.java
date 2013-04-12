package org.bouncycastle.math.ec;

import java.math.BigInteger;

/**
 * base class for points on elliptic curves.
 */
public abstract class ECPoint
{
    ECCurve        curve;
    ECFieldElement x;
    ECFieldElement y;

    protected boolean withCompression;

    protected ECMultiplier multiplier = null;

    protected PreCompInfo preCompInfo = null;

//    private static X9IntegerConverter converter = new X9IntegerConverter();

    protected ECPoint(ECCurve curve, ECFieldElement x, ECFieldElement y)
    {
        this.curve = curve;
        this.x = x;
        this.y = y;
    }

    public ECCurve getCurve()
    {
        return curve;
    }

    public ECFieldElement getX()
    {
        return x;
    }

    public ECFieldElement getY()
    {
        return y;
    }

    public boolean isInfinity()
    {
        return x == null && y == null;
    }

    public boolean isCompressed()
    {
        return withCompression;
    }

    @Override
    public boolean equals(
        Object  other)
    {
        if (other == this)
        {
            return true;
        }

        if (!(other instanceof ECPoint))
        {
            return false;
        }

        ECPoint o = (ECPoint)other;

        if (this.isInfinity())
        {
            return o.isInfinity();
        }

        return x.equals(o.x) && y.equals(o.y);
    }

    @Override
    public int hashCode()
    {
        if (this.isInfinity())
        {
            return 0;
        }

        return x.hashCode() ^ y.hashCode();
    }

//    /**
//     * Mainly for testing. Explicitly set the <code>ECMultiplier</code>.
//     * @param multiplier The <code>ECMultiplier</code> to be used to multiply
//     * this <code>ECPoint</code>.
//     */
//    public void setECMultiplier(ECMultiplier multiplier)
//    {
//        this.multiplier = multiplier;
//    }

    /**
     * Sets the <code>PreCompInfo</code>. Used by <code>ECMultiplier</code>s
     * to save the precomputation for this <code>ECPoint</code> to store the
     * precomputation result for use by subsequent multiplication.
     * @param preCompInfo The values precomputed by the
     * <code>ECMultiplier</code>.
     */
    void setPreCompInfo(PreCompInfo preCompInfo)
    {
        this.preCompInfo = preCompInfo;
    }

    public abstract byte[] getEncoded();

    public abstract ECPoint add(ECPoint b);
    public abstract ECPoint subtract(ECPoint b);
    public abstract ECPoint negate();
    public abstract ECPoint twice();

    /**
     * Sets the default <code>ECMultiplier</code>, unless already set.
     */
    synchronized void assertECMultiplier()
    {
        if (this.multiplier == null)
        {
            this.multiplier = new FpNafMultiplier();
        }
    }

    /**
     * Multiplies this <code>ECPoint</code> by the given number.
     * @param k The multiplicator.
     * @return <code>k * this</code>.
     */
    public ECPoint multiply(BigInteger k)
    {
        if (k.signum() < 0)
        {
            throw new IllegalArgumentException("The multiplicator cannot be negative");
        }

        if (this.isInfinity())
        {
            return this;
        }

        if (k.signum() == 0)
        {
            return this.curve.getInfinity();
        }

        assertECMultiplier();
        return this.multiplier.multiply(this, k, preCompInfo);
    }

    /**
     * Elliptic curve points over Fp
     */
    public static class Fp extends ECPoint
    {

        /**
         * Create a point which encodes with point compression.
         *
         * @param curve the curve to use
         * @param x affine x co-ordinate
         * @param y affine y co-ordinate
         */
        public Fp(ECCurve curve, ECFieldElement x, ECFieldElement y)
        {
            this(curve, x, y, false);
        }

        /**
         * Create a point that encodes with or without point compresion.
         *
         * @param curve the curve to use
         * @param x affine x co-ordinate
         * @param y affine y co-ordinate
         * @param withCompression if true encode with point compression
         */
        public Fp(ECCurve curve, ECFieldElement x, ECFieldElement y, boolean withCompression)
        {
            super(curve, x, y);

            if ((x != null && y == null) || (x == null && y != null))
            {
                throw new IllegalArgumentException("Exactly one of the field elements is null");
            }

            this.withCompression = withCompression;
        }

        /**
         * return the field element encoded with point compression. (S 4.3.6)
         */
        public byte[] getEncoded()
        {
            return null;
            // BEGIN connectbot-removed
//            if (this.isInfinity())
//            {
//                return new byte[1];
//            }
//
//            int qLength = converter.getByteLength(x);
//
//            if (withCompression)
//            {
//                byte    PC;
//
//                if (this.getY().toBigInteger().testBit(0))
//                {
//                    PC = 0x03;
//                }
//                else
//                {
//                    PC = 0x02;
//                }
//
//                byte[]  X = converter.integerToBytes(this.getX().toBigInteger(), qLength);
//                byte[]  PO = new byte[X.length + 1];
//
//                PO[0] = PC;
//                System.arraycopy(X, 0, PO, 1, X.length);
//
//                return PO;
//            }
//            else
//            {
//                byte[]  X = converter.integerToBytes(this.getX().toBigInteger(), qLength);
//                byte[]  Y = converter.integerToBytes(this.getY().toBigInteger(), qLength);
//                byte[]  PO = new byte[X.length + Y.length + 1];
//
//                PO[0] = 0x04;
//                System.arraycopy(X, 0, PO, 1, X.length);
//                System.arraycopy(Y, 0, PO, X.length + 1, Y.length);
//
//                return PO;
//            }
        }

        // B.3 pg 62
        @Override
        public ECPoint add(ECPoint b)
        {
            if (this.isInfinity())
            {
                return b;
            }

            if (b.isInfinity())
            {
                return this;
            }

            // Check if b = this or b = -this
            if (this.x.equals(b.x))
            {
                if (this.y.equals(b.y))
                {
                    // this = b, i.e. this must be doubled
                    return this.twice();
                }

                // this = -b, i.e. the result is the point at infinity
                return this.curve.getInfinity();
            }

            ECFieldElement gamma = b.y.subtract(this.y).divide(b.x.subtract(this.x));

            ECFieldElement x3 = gamma.square().subtract(this.x).subtract(b.x);
            ECFieldElement y3 = gamma.multiply(this.x.subtract(x3)).subtract(this.y);

            return new ECPoint.Fp(curve, x3, y3);
        }

        // B.3 pg 62
        @Override
        public ECPoint twice()
        {
            if (this.isInfinity())
            {
                // Twice identity element (point at infinity) is identity
                return this;
            }

            if (this.y.toBigInteger().signum() == 0)
            {
                // if y1 == 0, then (x1, y1) == (x1, -y1)
                // and hence this = -this and thus 2(x1, y1) == infinity
                return this.curve.getInfinity();
            }

            ECFieldElement TWO = this.curve.fromBigInteger(BigInteger.valueOf(2));
            ECFieldElement THREE = this.curve.fromBigInteger(BigInteger.valueOf(3));
            ECFieldElement gamma = this.x.square().multiply(THREE).add(curve.a).divide(y.multiply(TWO));

            ECFieldElement x3 = gamma.square().subtract(this.x.multiply(TWO));
            ECFieldElement y3 = gamma.multiply(this.x.subtract(x3)).subtract(this.y);

            return new ECPoint.Fp(curve, x3, y3, this.withCompression);
        }

        // D.3.2 pg 102 (see Note:)
        @Override
        public ECPoint subtract(ECPoint b)
        {
            if (b.isInfinity())
            {
                return this;
            }

            // Add -b
            return add(b.negate());
        }

        @Override
        public ECPoint negate()
        {
            return new ECPoint.Fp(curve, this.x, this.y.negate(), this.withCompression);
        }

        /**
         * Sets the default <code>ECMultiplier</code>, unless already set.
         */
        @Override
        synchronized void assertECMultiplier()
        {
            if (this.multiplier == null)
            {
                this.multiplier = new WNafMultiplier();
            }
        }
    }
}
