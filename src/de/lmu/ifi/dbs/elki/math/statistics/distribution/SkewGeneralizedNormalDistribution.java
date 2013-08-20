package de.lmu.ifi.dbs.elki.math.statistics.distribution;

/*
 This file is part of ELKI:
 Environment for Developing KDD-Applications Supported by Index-Structures

 Copyright (C) 2013
 Ludwig-Maximilians-Universität München
 Lehr- und Forschungseinheit für Datenbanksysteme
 ELKI Development Team

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import java.util.Random;

import de.lmu.ifi.dbs.elki.math.MathUtil;

/**
 * Generalized Gaussian distribution by adding a skew term, similar to lognormal distributions.
 * 
 * This is one kind of generalized normal distributions. Note that there are
 * multiple that go by the name of a "Generalized Normal Distribution".
 * 
 * @author Erich Schubert
 */
public class SkewGeneralizedNormalDistribution implements DistributionWithRandom {
  /**
   * Mean value for the generator
   */
  private double mean;

  /**
   * Standard deviation
   */
  private double stddev;

  /**
   * Skew.
   */
  private double skew;

  /**
   * The random generator.
   */
  private Random random;

  /**
   * Constructor for Gaussian distribution
   * 
   * @param mean Mean
   * @param stddev Standard Deviation
   * @param skew Skew
   * @param random Random generator
   */
  public SkewGeneralizedNormalDistribution(double mean, double stddev, double skew, Random random) {
    super();
    this.mean = mean;
    this.stddev = stddev;
    this.skew = skew;
    this.random = random;
  }

  /**
   * Constructor for Gaussian distribution
   * 
   * @param mean Mean
   * @param stddev Standard Deviation
   * @param skew Skew
   */
  public SkewGeneralizedNormalDistribution(double mean, double stddev, double skew) {
    this(mean, stddev, skew, null);
  }

  @Override
  public double pdf(double val) {
    return pdf(val, mean, stddev, skew);
  }

  @Override
  public double cdf(double val) {
    return cdf(val, mean, stddev, skew);
  }

  @Override
  public double quantile(double q) {
    return quantile(q, mean, stddev, skew);
  }

  @Override
  public double nextRandom() {
    double y = random.nextGaussian();
    if (Math.abs(skew) > 0.) {
      y = (1. - Math.exp(-skew * y)) / skew;
    }
    return mean + stddev * y;

  }

  @Override
  public String toString() {
    return "SkewNormalDistribution(mean=" + mean + ", stddev=" + stddev + ", skew=" + skew + ")";
  }

  /**
   * Probability density function of the skewed normal distribution.
   * 
   * @param x The value.
   * @param mu The mean.
   * @param sigma The standard deviation.
   * @return PDF of the given normal distribution at x.
   */
  public static double pdf(double x, double mu, double sigma, double skew) {
    x = (x - mu) / sigma;
    if (Math.abs(skew) > 0.) {
      x = -Math.log(1. - skew * x) / skew;
    }
    return MathUtil.SQRTHALF * Math.exp(-.5 * x * x) / sigma / (1 - skew * x);
  }

  /**
   * Cumulative probability density function (CDF) of a normal distribution.
   * 
   * @param x value to evaluate CDF at
   * @param mu Mean value
   * @param sigma Standard deviation.
   * @return The CDF of the given normal distribution at x.
   */
  public static double cdf(double x, double mu, double sigma, double skew) {
    x = (x - mu) / sigma;
    if (Math.abs(skew) > 0.) {
      x = -Math.log(1. - skew * x) / skew;
    }
    return .5 + .5 * NormalDistribution.erf(x * MathUtil.SQRTHALF);
  }

  /**
   * Inverse cumulative probability density function (probit) of a normal
   * distribution.
   * 
   * @param x value to evaluate probit function at
   * @param mu Mean value
   * @param sigma Standard deviation.
   * @return The probit of the given normal distribution at x.
   */
  public static double quantile(double x, double mu, double sigma, double skew) {
    x = NormalDistribution.standardNormalQuantile(x);
    if (Math.abs(skew) > 0.) {
      x = (1. - Math.exp(-skew * x)) / skew;
    }
    return mu + sigma * x;
  }
}