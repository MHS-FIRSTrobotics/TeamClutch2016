///*
// * Copyright © 2016 David Sargent
// * Permission is hereby granted, free of charge, to any person obtaining a copy of this software
// * and associated documentation files (the "Software"), to deal in the Software without restriction,
// * including without limitation  the rights to use, copy, modify, merge, publish, distribute, sublicense,
// * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to
// * the following conditions:
// *
// * The above copyright notice and this permission notice shall be included in all copies or
// * substantial portions of the Software.
// *
// * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
// * BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
// * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
// * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
// * FROM,OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
// */
//
//package org.ftccommunity.ftcxtensible.math;
//
//import org.junit.Test;
//
//import static org.junit.Assert.*;
//
//
//public class CartesianCoordinatesTest {
//    @Test
//    public void testPolarConstructor() throws Exception {
//        CartesianCoordinates cartesianCoordinates = new CartesianCoordinates(new PolarCoordinates(1, Math.PI));
//        assertEquals(cartesianCoordinates.getX(), -1, 0.01);
//        assertEquals(cartesianCoordinates.getY(), 0, 0.01);
//    }
//
//    @Test
//    public void testInvert() throws Exception {
//        CartesianCoordinates cartesianCoordinates = new CartesianCoordinates(4, 5);
//        CartesianCoordinates inversion  = cartesianCoordinates.invert();
//        assertEquals(5, inversion.getX(), 0);
//        assertEquals(4, inversion.getY(), 0);
//    }
//}