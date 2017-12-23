/*
 * Copyright (c) 2011-2017, Peter Abeles. All Rights Reserved.
 *
 * This file is part of BoofCV (http://boofcv.org).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package boofcv.abst.fiducial;

import boofcv.abst.filter.binary.InputToBinary;
import boofcv.alg.fiducial.qrcode.QrCode;
import boofcv.alg.fiducial.qrcode.QrCodeDecoder;
import boofcv.alg.fiducial.qrcode.QrCodePositionPatternDetector;
import boofcv.alg.shapes.polygon.DetectPolygonBinaryGrayRefine;
import boofcv.misc.MovingAverage;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageGray;

import java.util.List;

/**
 * A QR-Code detector which is designed to find the location of corners in the finder pattern precisely.
 *
 * @param <T>
 */
public class QrCodePreciseDetector<T extends ImageGray<T>> implements QrCodeDetector<T>
{
	QrCodePositionPatternDetector<T> detectPositionPatterns;
	QrCodeDecoder<T> decoder;
	InputToBinary<T> inputToBinary;
	Class<T> imageType;

	GrayU8 binary = new GrayU8(1,1);

	// runtime profiling
	protected MovingAverage milliBinary = new MovingAverage(0.8);
	protected MovingAverage milliDecoding = new MovingAverage(0.8);

	public QrCodePreciseDetector(InputToBinary<T> inputToBinary,
								 QrCodePositionPatternDetector<T> detectPositionPatterns ,
								 Class<T> imageType ) {
		this.inputToBinary = inputToBinary;
		this.detectPositionPatterns = detectPositionPatterns;
		this.decoder = new QrCodeDecoder<>(imageType);
		this.imageType = imageType;
	}

	@Override
	public void process(T gray) {
		long time0 = System.nanoTime();
		binary.reshape(gray.width,gray.height);
		inputToBinary.process(gray,binary);
		long time1 = System.nanoTime();
		milliBinary.update((time1-time0)*1e-6);

		System.out.printf("qrcode: binary %5.2f ",milliBinary.getAverage());

		detectPositionPatterns.process(gray,binary);
		time0 = System.nanoTime();
		decoder.process(detectPositionPatterns.getPositionPatterns(),gray);
		time1 = System.nanoTime();
		milliDecoding.update((time1-time0)*1e-6);

		System.out.printf(" decoding %5.1f\n",milliDecoding.getAverage());

		System.out.println("Failed "+decoder.getFailures().size());
		for( QrCode qr : decoder.getFailures() ) {
			System.out.println("  cause "+qr.failureCause);
		}
	}

	@Override
	public List<QrCode> getDetections() {
		return decoder.getFound();
	}

	@Override
	public List<QrCode> getFailures() {
		return decoder.getFailures();
	}

	public GrayU8 getBinary() {
		return binary;
	}

	public void resetRuntimeProfiling() {
		milliBinary.reset();
		milliDecoding.reset();
		detectPositionPatterns.resetRuntimeProfiling();
	}

	public QrCodePositionPatternDetector<T> getDetectPositionPatterns() {
		return detectPositionPatterns;
	}

	public DetectPolygonBinaryGrayRefine<T> getSquareDetector() {
		return detectPositionPatterns.getSquareDetector();
	}

	public QrCodeDecoder<T> getDecoder() {
		return decoder;
	}

	@Override
	public Class<T> getImageType() {
		return imageType;
	}
}
