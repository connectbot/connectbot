/*
 * Copyright 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.keyczar.jce;

import java.math.BigInteger;
import java.security.spec.ECFieldFp;
import java.security.spec.ECParameterSpec;

/**
 * This class implements the basic EC operations such as point addition and
 * doubling and point multiplication. Only NSA Suite B / NIST curves are
 * supported.
 *
 * Todo:
 *  - Add (more) comments - Performance optimizations - Cleanup ASN.1 code,
 * possibly replace with own impl - ...
 *
 * References:
 *
 * [1] Software Implementation of the NIST Elliptic Curves Over Prime Fields, M.
 * Brown et al. [2] Efficient elliptic curve exponentiation using mixed
 * coordinates, H. Cohen et al. [3] SEC 1: Elliptic Curve Cryptography. [4]
 * Guide to Elliptic Curve Cryptography, D. Hankerson et al., Springer.
 *
 * @author martclau@gmail.com
 *
 */
// BEGIN connectbot-changed
public final class EcCore {
// END connectbot-changed
// BEGIN connectbot-removed
//  private static final long serialVersionUID = -1376116429660095993L;
//
//  private static final String INFO = "Google Keyczar (EC key/parameter generation; EC signing)";
//
//  public static final String NAME = "GooKey";
//
//  @SuppressWarnings("unchecked")
//  public EcCore() {
//    super(NAME, 0.1, INFO);
//    AccessController.doPrivileged(new PrivilegedAction<Object>() {
//      @Override
//      public Object run() {
//        put("Signature.SHA1withECDSA", "org.keyczar.jce.EcSignatureImpl$SHA1");
//        put("Alg.Alias.Signature.ECDSA", "SHA1withDSA");
//        put("Signature.SHA256withECDSA",
//            "org.keyczar.jce.EcSignatureImpl$SHA256");
//        put("Signature.SHA384withECDSA",
//            "org.keyczar.jce.EcSignatureImpl$SHA384");
//        put("Signature.SHA512withECDSA",
//            "org.keyczar.jce.EcSignatureImpl$SHA512");
//        put("KeyPairGenerator.EC", "org.keyczar.jce.EcKeyPairGeneratorImpl");
//        put("KeyFactory.EC", "org.keyczar.jce.EcKeyFactoryImpl");
//        put("Signature.SHA1withECDSA KeySize", "521");
//        put("Signature.SHA1withECDSA ImplementedIn", "Software");
//        put("Signature.SHA256withECDSA KeySize", "521");
//        put("Signature.SHA256withECDSA ImplementedIn", "Software");
//        put("Signature.SHA384withECDSA KeySize", "521");
//        put("Signature.SHA384withECDSA ImplementedIn", "Software");
//        put("Signature.SHA512withECDSA KeySize", "521");
//        put("Signature.SHA512withECDSA ImplementedIn", "Software");
//        put("KeyPairGenerator.EC ImplementedIn", "Software");
//        put("KeyFactory.EC ImplementedIn", "Software");
//        return null;
//      }
//    });
//  }
//
//  private static final ECParameterSpec P192 = new ECParameterSpec(
//      new EllipticCurve(
//          new ECFieldFp(new BigInteger(
//              "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFFFFFFFFFFFF", 16)),
//          new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFFFFFFFFFFFC", 16),
//          new BigInteger("64210519E59C80E70FA7E9AB72243049FEB8DEECC146B9B1", 16)),
//      new ECPoint(
//          new BigInteger("188DA80EB03090F67CBF20EB43A18800F4FF0AFD82FF1012", 16),
//          new BigInteger("07192B95FFC8DA78631011ED6B24CDD573F977A11E794811", 16)),
//      new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFF99DEF836146BC9B1B4D22831", 16), 1);
//
//  private static final ECParameterSpec P224 = new ECParameterSpec(
//      new EllipticCurve(new ECFieldFp(new BigInteger(
//          "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF000000000000000000000001", 16)),
//          new BigInteger(
//              "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFFFFFFFFFFFFFFFFFFFE", 16),
//          new BigInteger(
//              "B4050A850C04B3ABF54132565044B0B7D7BFD8BA270B39432355FFB4", 16)),
//      new ECPoint(new BigInteger(
//          "B70E0CBD6BB4BF7F321390B94A03C1D356C21122343280D6115C1D21", 16),
//          new BigInteger(
//              "BD376388B5F723FB4C22DFE6CD4375A05A07476444D5819985007E34", 16)),
//      new BigInteger(
//          "FFFFFFFFFFFFFFFFFFFFFFFFFFFF16A2E0B8F03E13DD29455C5C2A3D", 16), 1);
//
//  private static final ECParameterSpec P256 = new ECParameterSpec(
//      new EllipticCurve(new ECFieldFp(new BigInteger(
//          "FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFF",
//          16)), new BigInteger(
//          "FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFC",
//          16), new BigInteger(
//          "5AC635D8AA3A93E7B3EBBD55769886BC651D06B0CC53B0F63BCE3C3E27D2604B",
//          16)), new ECPoint(new BigInteger(
//          "6B17D1F2E12C4247F8BCE6E563A440F277037D812DEB33A0F4A13945D898C296",
//          16), new BigInteger(
//          "4FE342E2FE1A7F9B8EE7EB4A7C0F9E162BCE33576B315ECECBB6406837BF51F5",
//          16)), new BigInteger(
//          "FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551",
//          16), 1);
//
//  private static final ECParameterSpec P384 = new ECParameterSpec(
//      new EllipticCurve(
//          new ECFieldFp(
//              new BigInteger(
//                  "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFFFF0000000000000000FFFFFFFF",
//                  16)),
//          new BigInteger(
//              "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFFFF0000000000000000FFFFFFFC",
//              16),
//          new BigInteger(
//              "B3312FA7E23EE7E4988E056BE3F82D19181D9C6EFE8141120314088F5013875AC656398D8A2ED19D2A85C8EDD3EC2AEF",
//              16)),
//      new ECPoint(
//          new BigInteger(
//              "AA87CA22BE8B05378EB1C71EF320AD746E1D3B628BA79B9859F741E082542A385502F25DBF55296C3A545E3872760AB7",
//              16),
//          new BigInteger(
//              "3617DE4A96262C6F5D9E98BF9292DC29F8F41DBD289A147CE9DA3113B5F0B8C00A60B1CE1D7E819D7A431D7C90EA0E5F",
//              16)),
//      new BigInteger(
//          "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFC7634D81F4372DDF581A0DB248B0A77AECEC196ACCC52973",
//          16), 1);
//
//  private static final ECParameterSpec P521 = new ECParameterSpec(
//      new EllipticCurve(
//          new ECFieldFp(
//              new BigInteger(
//                  "01FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF",
//                  16)),
//          new BigInteger(
//              "01FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFC",
//              16),
//          new BigInteger(
//              "0051953EB9618E1C9A1F929A21A0B68540EEA2DA725B99B315F3B8B489918EF109E156193951EC7E937B1652C0BD3BB1BF073573DF883D2C34F1EF451FD46B503F00",
//              16)),
//      new ECPoint(
//          new BigInteger(
//              "00C6858E06B70404E9CD9E3ECB662395B4429C648139053FB521F828AF606B4D3DBAA14B5E77EFE75928FE1DC127A2FFA8DE3348B3C1856A429BF97E7E31C2E5BD66",
//              16),
//          new BigInteger(
//              "011839296A789A3BC0045C8A5FB42C7D1BD998F54449579B446817AFBD17273E662C97EE72995EF42640C550B9013FAD0761353C7086A272C24088BE94769FD16650",
//              16)),
//      new BigInteger(
//          "01FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFA51868783BF2F966B7FCC0148F709A5D03BB5C9B8899C47AEBB6FB71E91386409",
//          16), 1);
//
//  public static final String EC_PARAMS_P192_OID = "1.2.840.10045.3.1.1";
//  public static final String EC_PARAMS_P224_OID = "1.3.132.0.33";
//  public static final String EC_PARAMS_P256_OID = "1.2.840.10045.3.1.7";
//  public static final String EC_PARAMS_P384_OID = "1.3.132.0.34";
//  public static final String EC_PARAMS_P521_OID = "1.3.132.0.35";
//
//  private static Map<String, ECParameterSpec> oidMap = new HashMap<String, ECParameterSpec>();
//  private static Map<ECParameterSpec, String> paramsMap = new HashMap<ECParameterSpec, String>();
//  private static Map<ECParameterSpec, String> friendlyNameMap = new HashMap<ECParameterSpec, String>();
//
//  static {
//    oidMap.put(EC_PARAMS_P192_OID, P192);
//    oidMap.put(EC_PARAMS_P224_OID, P224);
//    oidMap.put(EC_PARAMS_P256_OID, P256);
//    oidMap.put(EC_PARAMS_P384_OID, P384);
//    oidMap.put(EC_PARAMS_P521_OID, P521);
//    paramsMap.put(P192, EC_PARAMS_P192_OID);
//    paramsMap.put(P224, EC_PARAMS_P224_OID);
//    paramsMap.put(P256, EC_PARAMS_P256_OID);
//    paramsMap.put(P384, EC_PARAMS_P384_OID);
//    paramsMap.put(P521, EC_PARAMS_P521_OID);
//    friendlyNameMap.put(P192, "P-192");
//    friendlyNameMap.put(P224, "P-224");
//    friendlyNameMap.put(P256, "P-256");
//    friendlyNameMap.put(P384, "P-384");
//    friendlyNameMap.put(P521, "P-521");
//  }
//
//  public static ECParameterSpec getParams(String oid) {
//    ECParameterSpec params;
//    if ((params = oidMap.get(oid)) != null) return params;
//    throw new IllegalArgumentException("Unsupported EC parameters: " + oid);
//  }
//
//  public static String getOID(ECParameterSpec params) {
//    String oid;
//    if ((oid = paramsMap.get(params)) != null) return oid;
//    throw new IllegalArgumentException("Unsupport EC parameters");
//  }
//
//  public static String getFriendlyName(ECParameterSpec params) {
//    String name;
//    if ((name = friendlyNameMap.get(params)) != null) return name;
//    throw new IllegalArgumentException("Unsupport EC parameters");
//  }
//
//  private static final BigInteger ZERO = BigInteger.ZERO;
//  private static final BigInteger ONE = BigInteger.ONE;
//  private static final BigInteger TWO = BigInteger.valueOf(2);
// END connectbot-removed
  private static final BigInteger THREE = BigInteger.valueOf(3);
// BEGIN connectbot-removed
//  private static final BigInteger FOUR = BigInteger.valueOf(4);
//  private static final BigInteger EIGHT = BigInteger.valueOf(8);
// END connectbot-removed

  private static BigInteger[] doublePointA(BigInteger[] P,
      ECParameterSpec params) {
    final BigInteger p = ((ECFieldFp) params.getCurve().getField()).getP();
    final BigInteger a = params.getCurve().getA();

    if (P[0] == null || P[1] == null) return P;

    BigInteger d = (P[0].pow(2).multiply(THREE).add(a)).multiply(P[1]
        .shiftLeft(1).modInverse(p));
    BigInteger[] R = new BigInteger[2];
    R[0] = d.pow(2).subtract(P[0].shiftLeft(1)).mod(p);
    R[1] = d.multiply(P[0].subtract(R[0])).subtract(P[1]).mod(p);

    return R;
  }

  private static BigInteger[] addPointsA(BigInteger[] P1, BigInteger[] P2,
      ECParameterSpec params) {
    final BigInteger p = ((ECFieldFp) params.getCurve().getField()).getP();

    if (P2[0] == null || P2[1] == null) return P1;

    if (P1[0] == null || P1[1] == null) return P2;

    BigInteger d = (P2[1].subtract(P1[1])).multiply((P2[0].subtract(P1[0]))
        .modInverse(p));
    BigInteger[] R = new BigInteger[2];
    R[0] = d.pow(2).subtract(P1[0]).subtract(P2[0]).mod(p);
    R[1] = d.multiply(P1[0].subtract(R[0])).subtract(P1[1]).mod(p);

    return R;
  }

  public static BigInteger[] multiplyPointA(BigInteger[] P, BigInteger k,
      ECParameterSpec params) {
    BigInteger[] Q = new BigInteger[] {null, null};

    for (int i = k.bitLength() - 1; i >= 0; i--) {
      Q = doublePointA(Q, params);
      if (k.testBit(i)) Q = addPointsA(Q, P, params);
    }

    return Q;
  }

// BEGIN connectbot-removed
//  private static BigInteger[] doublePointJ(BigInteger[] P,
//      ECParameterSpec params) {
//    final BigInteger p = ((ECFieldFp) params.getCurve().getField()).getP();
//    BigInteger A, B, C, D;
//
//    if (P[2].signum() == 0) // point at inf
//      return P;
//
//    A = FOUR.multiply(P[0]).multiply(P[1].pow(2)).mod(p);
//    B = EIGHT.multiply(P[1].pow(4)).mod(p);
//    C = THREE.multiply(P[0].subtract(P[2].pow(2))).multiply(
//        P[0].add(P[2].pow(2))).mod(p);
//    D = C.pow(2).subtract(A.add(A)).mod(p);
//
//    return new BigInteger[] {
//        D, C.multiply(A.subtract(D)).subtract(B).mod(p),
//        TWO.multiply(P[1]).multiply(P[2]).mod(p)};
//  }
//
//  private static BigInteger[] addPointsJA(BigInteger[] P1, BigInteger[] P2,
//      ECParameterSpec params) {
//    final BigInteger p = ((ECFieldFp) params.getCurve().getField()).getP();
//    BigInteger A, B, C, D;
//    BigInteger X3;
//
//    if (P1[2].signum() == 0) // point at inf
//      return new BigInteger[] {P2[0], P2[1], ONE};
//
//    A = P2[0].multiply(P1[2].pow(2)).mod(p);
//    B = P2[1].multiply(P1[2].pow(3)).mod(p);
//    C = A.subtract(P1[0]).mod(p);
//    D = B.subtract(P1[1]).mod(p);
//
//    X3 = D.pow(2)
//        .subtract(C.pow(3).add(TWO.multiply(P1[0]).multiply(C.pow(2)))).mod(p);
//    return new BigInteger[] {
//        X3,
//        D.multiply(P1[0].multiply(C.pow(2)).subtract(X3)).subtract(
//            P1[1].multiply(C.pow(3))).mod(p), P1[2].multiply(C).mod(p)};
//  }
//
//  // Binary NAF method for point multiplication
//  public static BigInteger[] multiplyPoint(BigInteger[] P, BigInteger k,
//      ECParameterSpec params) {
//    BigInteger h = THREE.multiply(k);
//
//    BigInteger[] Pneg = new BigInteger[] {P[0], P[1].negate()};
//    BigInteger[] R = new BigInteger[] {P[0], P[1], ONE};
//
//    int bitLen = h.bitLength();
//    for (int i = bitLen - 2; i > 0; --i) {
//      R = doublePointJ(R, params);
//      if (h.testBit(i)) R = addPointsJA(R, P, params);
//      if (k.testBit(i)) R = addPointsJA(R, Pneg, params);
//    }
//
//    // // <DEBUG>
//    // BigInteger[] SS = new BigInteger[] { R[0], R[1], R[2] };
//    // toAffine(SS, params);
//    // BigInteger[] RR = multiplyPointA(P, k, params);
//    // if (!SS[0].equals(RR[0]) || !SS[1].equals(RR[1]))
//    // throw new RuntimeException("Internal mult error");
//    // // </DEBUG>
//
//    return R;
//  }

//  // Simultaneous multiple point multiplication, also known as Shamir's trick
//  static BigInteger[] multiplyPoints(BigInteger[] P, BigInteger k,
//      BigInteger[] Q, BigInteger l, ECParameterSpec params) {
//    BigInteger[] PQ = addPointsA(P, Q, params);
//    BigInteger[] R = new BigInteger[] {null, null, ZERO};
//
//    int max = Math.max(k.bitLength(), l.bitLength());
//    for (int i = max - 1; i >= 0; --i) {
//      R = doublePointJ(R, params);
//      if (k.testBit(i)) {
//        if (l.testBit(i))
//          R = addPointsJA(R, PQ, params);
//        else
//          R = addPointsJA(R, P, params);
//      } else if (l.testBit(i)) R = addPointsJA(R, Q, params);
//    }
//
//    // // <DEBUG>
//    // BigInteger[] SS = new BigInteger[] { R[0], R[1], R[2] };
//    // toAffine(SS, params);
//    // BigInteger[] AA = multiplyPointA(P, k, params);
//    // BigInteger[] BB = multiplyPointA(Q, l, params);
//    // BigInteger[] AB = addPointsA(AA, BB, params);
//    // if (!SS[0].equals(AB[0]) || !SS[1].equals(AB[1]))
//    // throw new RuntimeException("Internal mult error");
//    // // </DEBUG>
//
//    return R;
//  }
//
//  // SEC 1, 2.3.5
//  static byte[] fieldElemToBytes(BigInteger a, ECParameterSpec params) {
//    int len = (((ECFieldFp) params.getCurve().getField()).getP().bitLength() + 7) / 8;
//    byte[] bytes = a.toByteArray();
//    if (len < bytes.length) {
//      byte[] tmp = new byte[len];
//      System.arraycopy(bytes, bytes.length - tmp.length, tmp, 0, tmp.length);
//      return tmp;
//    } else if (len > bytes.length) {
//      byte[] tmp = new byte[len];
//      System.arraycopy(bytes, 0, tmp, tmp.length - bytes.length, bytes.length);
//      return tmp;
//    }
//    return bytes;
//  }
//
//  static int fieldElemToBytes(BigInteger a, ECParameterSpec params,
//      byte[] data, int off) {
//    int len = (((ECFieldFp) params.getCurve().getField()).getP().bitLength() + 7) / 8;
//    byte[] bytes = a.toByteArray();
//    if (len < bytes.length) {
//      System.arraycopy(bytes, bytes.length - len, data, off, len);
//      return len;
//    } else if (len > bytes.length) {
//      System.arraycopy(bytes, 0, data, len - bytes.length + off, bytes.length);
//      return len;
//    }
//    System.arraycopy(bytes, 0, data, off, bytes.length);
//    return bytes.length;
//  }
//
//  // SEC 1, 2.3.3
//  static byte[] ecPointToBytes(ECPoint a, ECParameterSpec params) {
//    byte[] fe1 = fieldElemToBytes(a.getAffineX(), params);
//    byte[] fe2 = fieldElemToBytes(a.getAffineY(), params);
//    byte[] bytes = new byte[1 + fe1.length + fe2.length];
//    bytes[0] = 0x04;
//    System.arraycopy(fe1, 0, bytes, 1, fe1.length);
//    System.arraycopy(fe2, 0, bytes, 1 + fe1.length, fe2.length);
//    return bytes;
//  }
//
//  // SEC 1, 2.3.4
//  static ECPoint bytesToECPoint(byte[] bytes, ECParameterSpec params) {
//    switch (bytes[0]) {
//    case 0x00: // point at inf
//      throw new IllegalArgumentException(
//          "Point at infinity is not a valid argument");
//    case 0x02: // point compression
//    case 0x03:
//      throw new UnsupportedOperationException(
//          "Point compression is not supported");
//    case 0x04:
//      final BigInteger p = ((ECFieldFp) params.getCurve().getField()).getP();
//      byte[] fe = new byte[(p.bitLength() + 7) / 8];
//      System.arraycopy(bytes, 1, fe, 0, fe.length);
//      BigInteger x = new BigInteger(1, fe);
//      System.arraycopy(bytes, 1 + fe.length, fe, 0, fe.length);
//      return new ECPoint(x, new BigInteger(1, fe));
//    default:
//      throw new IllegalArgumentException("Invalid point encoding");
//    }
//  }
//
//  // Convert Jacobian point to affine
//  static void toAffine(BigInteger[] P, ECParameterSpec params) {
//    final BigInteger p = ((ECFieldFp) params.getCurve().getField()).getP();
//    P[0] = P[0].multiply(P[2].pow(2).modInverse(p)).mod(p);
//    P[1] = P[1].multiply(P[2].pow(3).modInverse(p)).mod(p);
//  }
//
//  static void toAffineX(BigInteger[] P, ECParameterSpec params) {
//    final BigInteger p = ((ECFieldFp) params.getCurve().getField()).getP();
//    P[0] = P[0].multiply(P[2].pow(2).modInverse(p)).mod(p);
//  }
//
//  static BigInteger[] internalPoint(ECPoint P) {
//    return new BigInteger[] {P.getAffineX(), P.getAffineY()};
//  }
//
//  // private static void printPerf(String msg, long start, long stop) {
//  // String unit = "ms";
//  // long diff = stop - start;
//  // if (diff > 1000) {
//  // diff /= 1000;
//  // unit = "s";
//  // }
//  // System.out.printf("%s: %d %s\n", msg, diff, unit);
//  // }
//
//  public static void main(String[] args) throws Exception {
//
//    Security.insertProviderAt(new EcCore(), 0);
//
//    // ----
//    // Test primitives
//    // ----
//
//    // GooKey EC private key, 256 bit
//    // Private value:
//    // a9231e0d113abdacd3bb5edb24124fbef6f562c5f90b835670f5e48f775019f2
//    // Parameters: P-256 (1.2.840.10045.3.1.7)
//    // GooKey EC public key, 256 bit
//    // Public value (x coordinate):
//    // 86645e0320c0f9dc1a9b8456396cc105754df67a9829c21e13ab6ecf944cf68c
//    // Public value (y coordinate):
//    // ea1721a578043d48f12738359b5eb5f0dac2242ec6128ee0ab6ff40c8fe0cae6
//    // Parameters: P-256 (1.2.840.10045.3.1.7)
//    // GooKey EC private key, 256 bit
//    // Private value:
//    // b84d5cfab214fc3928864abb85f668a85b1006ca0147c78f22deb1dcc7e4a022
//    // Parameters: P-256 (1.2.840.10045.3.1.7)
//    // GooKey EC public key, 256 bit
//    // Public value (x coordinate):
//    // 61f6f7264f0a19f0debcca3efd079667a0112cc0b8be07a815b4c375e96ad3d1
//    // Public value (y coordinate):
//    // 3308c0016d776ed5aa9f021e43348b2e684b3b7a0f25dc9e4c8670b5d87cb705
//    // Parameters: P-256 (1.2.840.10045.3.1.7)
//
//    // P = kG
//    BigInteger k = new BigInteger(
//        "a9231e0d113abdacd3bb5edb24124fbef6f562c5f90b835670f5e48f775019f2", 16);
//    BigInteger[] P = new BigInteger[] {
//        new BigInteger(
//            "86645e0320c0f9dc1a9b8456396cc105754df67a9829c21e13ab6ecf944cf68c",
//            16),
//        new BigInteger(
//            "ea1721a578043d48f12738359b5eb5f0dac2242ec6128ee0ab6ff40c8fe0cae6",
//            16), ONE};
//
//    // Q = lG
//    BigInteger l = new BigInteger(
//        "b84d5cfab214fc3928864abb85f668a85b1006ca0147c78f22deb1dcc7e4a022", 16);
//    BigInteger[] Q = new BigInteger[] {
//        new BigInteger(
//            "61f6f7264f0a19f0debcca3efd079667a0112cc0b8be07a815b4c375e96ad3d1",
//            16),
//        new BigInteger(
//            "3308c0016d776ed5aa9f021e43348b2e684b3b7a0f25dc9e4c8670b5d87cb705",
//            16), ONE};
//
//    // Known answer for P+Q
//    BigInteger[] kat1 = new BigInteger[] {
//        new BigInteger(
//            "bc7adb05bca2460bbfeb4e0f88b61c384ea88ed3fd56017938ac2582513d4220",
//            16),
//        new BigInteger(
//            "a640a43df2e9df39eec11445b7e3f7835b743ef1ac4a83cecb570a060b3f1c6c",
//            16)};
//
//    BigInteger[] R = addPointsA(P, Q, P256);
//    if (!R[0].equals(kat1[0]) || !R[1].equals(kat1[1]))
//      throw new RuntimeException("kat1 failed");
//
//    R = addPointsJA(P, Q, P256);
//    toAffine(R, P256);
//    if (!R[0].equals(kat1[0]) || !R[1].equals(kat1[1]))
//      throw new RuntimeException("kat1 failed");
//
//
//    // Known answer for Q+Q
//    BigInteger[] kat2 = new BigInteger[] {
//        new BigInteger(
//            "c79d7f9100c14a70f0bb9bdce59654abf99e10d1ac5afc1a0f1b6bc650d6429b",
//            16),
//        new BigInteger(
//            "6856814e47adce42bc0d7c3bef308c6c737c418ed093effb31e21f53c7735c97",
//            16)};
//
//    R = doublePointA(P, P256);
//    if (!R[0].equals(kat2[0]) || !R[1].equals(kat2[1]))
//      throw new RuntimeException("kat2 failed");
//
//    R = doublePointJ(P, P256);
//    toAffine(R, P256);
//    if (!R[0].equals(kat2[0]) || !R[1].equals(kat2[1]))
//      throw new RuntimeException("kat2 failed");
//
//    // Known answer for kP
//    BigInteger[] kat3 = new BigInteger[] {
//        new BigInteger(
//            "97a82a834b9e6b50660ae30d43dac9b200276e8bcd2ed6a6593048de09276d1a",
//            16),
//        new BigInteger(
//            "30a9590a01066d8ef54a910afcc8648dbc7400c01750af423ce95547f2154d56",
//            16)};
//
//    R = multiplyPointA(P, k, P256);
//    if (!R[0].equals(kat3[0]) || !R[1].equals(kat3[1]))
//      throw new RuntimeException("kat3 failed");
//
//    R = multiplyPoint(P, k, P256);
//    toAffine(R, P256);
//    if (!R[0].equals(kat3[0]) || !R[1].equals(kat3[1]))
//      throw new RuntimeException("kat3 failed");
//
//    // Known answer for kP+lQ
//    BigInteger[] kat4 = new BigInteger[] {
//        new BigInteger(
//            "6fd51be5cf3d6a6bcb62594bbe41ccf549b37d8fefff6e293a5bea0836efcfc6",
//            16),
//        new BigInteger(
//            "9bc21a930137aa3814908974c431e4545a05dce61321253c337f3883129c42ca",
//            16)};
//
//    BigInteger[] RR = multiplyPointA(Q, l, P256);
//    R = addPointsA(R, RR, P256);
//    if (!R[0].equals(kat4[0]) || !R[1].equals(kat4[1]))
//      throw new RuntimeException("kat4 failed");
//
//    R = multiplyPoints(P, k, Q, l, P256);
//    toAffine(R, P256);
//    if (!R[0].equals(kat4[0]) || !R[1].equals(kat4[1]))
//      throw new RuntimeException("kat4 failed");
//
//    // ----
//    // Test ECDSA in various combinations
//    // ----
//
//    Provider gooProv = Security.getProvider("GooKey");
//    Provider nssProv = Security.getProvider("SunPKCS11-NSS");
//
//    // Number of iterations: trust me, this is a (stress) good test
//    // and does provoke bugs in a fuzzing way.
//    int iter = 50;
//
//    // Iterate over all key lengths and signature schemes.
//    int[] keyLengths = {192, 224, 256, 384, 521};
//    String[] ecdsas = {
//        "SHA1withECDSA", "SHA256withECDSA", "SHA384withECDSA",
//        "SHA512withECDSA"};
//    for (int s = 0; s < ecdsas.length; s++) {
//      System.out.println("Signature scheme " + ecdsas[s]);
//      for (int i = 0; i < keyLengths.length; i++) {
//        System.out.print("Testing P-" + keyLengths[i] + ": ");
//        for (int n = 0; n < iter; n++) {
//          System.out.print(".");
//
//          KeyPairGenerator kpGen = KeyPairGenerator.getInstance("EC", gooProv);
//          kpGen.initialize(keyLengths[i]);
//          KeyPair ecKeyPair = kpGen.generateKeyPair();
//
//          ECPrivateKey ecPrivKey = (ECPrivateKey) ecKeyPair.getPrivate();
//          byte[] tmp = ecPrivKey.getEncoded();
//          KeyFactory keyFab = KeyFactory.getInstance("EC", gooProv);
//          keyFab.generatePrivate(new PKCS8EncodedKeySpec(tmp));
//          ECPrivateKeySpec ecPrivSpec = new ECPrivateKeySpec(ecPrivKey.getS(),
//              ecPrivKey.getParams());
//          keyFab.generatePrivate(ecPrivSpec);
//
//          ECPublicKey ecPubKey = (ECPublicKey) ecKeyPair.getPublic();
//          tmp = ecPubKey.getEncoded(); // dont modify tmp now - is used below
//          keyFab.generatePublic(new X509EncodedKeySpec(tmp));
//          ECPublicKeySpec ecPubSpec = new ECPublicKeySpec(ecPubKey.getW(),
//              ecPubKey.getParams());
//          keyFab.generatePublic(ecPubSpec);
//
//          Signature ecdsa = Signature.getInstance(ecdsas[s], gooProv);
//          ecdsa.initSign(ecPrivKey);
//          ecdsa.update(tmp);
//          byte[] sig = ecdsa.sign();
//          ecdsa.initVerify(ecPubKey);
//          ecdsa.update(tmp);
//          if (!ecdsa.verify(sig))
//            throw new RuntimeException("Signature not verified: "
//                + keyLengths[i]);
//
//          // Cross verify using NSS if present
//          if (nssProv != null) {
//            keyFab = KeyFactory.getInstance("EC", nssProv);
//
//            // For some reason NSS doesnt seem to work for P-192 and P-224?!
//            if (keyLengths[i] == 192 || keyLengths[i] == 224) continue;
//
//            ECPrivateKey nssPrivKey = (ECPrivateKey) keyFab
//                .generatePrivate(new PKCS8EncodedKeySpec(ecPrivKey.getEncoded()));
//            ECPublicKey nssPubKey = (ECPublicKey) keyFab
//                .generatePublic(new X509EncodedKeySpec(ecPubKey.getEncoded()));
//
//            ecdsa = Signature.getInstance(ecdsas[s], nssProv);
//            ecdsa.initVerify(nssPubKey);
//            ecdsa.update(tmp);
//            if (!ecdsa.verify(sig))
//              throw new RuntimeException("Signature not verified 2: "
//                  + keyLengths[i]);
//
//            ecdsa.initSign(nssPrivKey);
//            ecdsa.update(tmp);
//            sig = ecdsa.sign();
//            ecdsa = Signature.getInstance(ecdsas[s], gooProv);
//            ecdsa.initVerify(ecPubKey);
//            ecdsa.update(tmp);
//            if (!ecdsa.verify(sig))
//              throw new RuntimeException("Signature not verified 3: "
//                  + keyLengths[i]);
//          }
//        }
//        System.out.println(" done");
//      }
//    }
//
//    // Test Keyczar integration
//    // Signer ecdsaSigner = new Signer("c:\\temp\\eckeyset");
//    // String tbs = "Sign this";
//    // String sig = ecdsaSigner.sign(tbs);
//    // if (ecdsaSigner.verify(sig, tbs))
//    // System.out.println("Keyczar EC OK");
//    // else
//    // System.out.println("Keyczar EC not OK");
//  }
//END connectbot-removed
}
