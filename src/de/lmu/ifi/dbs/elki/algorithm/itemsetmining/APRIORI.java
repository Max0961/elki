package de.lmu.ifi.dbs.elki.algorithm.itemsetmining;

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

import gnu.trove.iterator.TLongIntIterator;
import gnu.trove.map.hash.TLongIntHashMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import de.lmu.ifi.dbs.elki.algorithm.AbstractAlgorithm;
import de.lmu.ifi.dbs.elki.data.BitVector;
import de.lmu.ifi.dbs.elki.data.type.TypeInformation;
import de.lmu.ifi.dbs.elki.data.type.TypeUtil;
import de.lmu.ifi.dbs.elki.data.type.VectorFieldTypeInformation;
import de.lmu.ifi.dbs.elki.database.ids.DBIDIter;
import de.lmu.ifi.dbs.elki.database.relation.Relation;
import de.lmu.ifi.dbs.elki.database.relation.RelationUtil;
import de.lmu.ifi.dbs.elki.logging.Logging;
import de.lmu.ifi.dbs.elki.logging.statistics.Duration;
import de.lmu.ifi.dbs.elki.result.AprioriResult;
import de.lmu.ifi.dbs.elki.utilities.BitsUtil;
import de.lmu.ifi.dbs.elki.utilities.documentation.Description;
import de.lmu.ifi.dbs.elki.utilities.documentation.Reference;
import de.lmu.ifi.dbs.elki.utilities.documentation.Title;
import de.lmu.ifi.dbs.elki.utilities.exceptions.AbortException;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.AbstractParameterizer;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.OptionID;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.constraints.CommonConstraints;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.constraints.OneMustBeSetGlobalConstraint;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.constraints.OnlyOneIsAllowedToBeSetGlobalConstraint;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.DoubleParameter;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.IntParameter;

/**
 * Provides the APRIORI algorithm for Mining Association Rules.
 * <p>
 * Reference: <br>
 * R. Agrawal, R. Srikant: Fast Algorithms for Mining Association Rules in Large
 * Databases. <br>
 * In Proc. 20th Int. Conf. on Very Large Data Bases (VLDB '94), Santiago de
 * Chile, Chile 1994.
 * </p>
 * 
 * @author Arthur Zimek
 * @author Erich Schubert
 * 
 * @apiviz.has Itemset
 * @apiviz.uses BitVector
 */
@Title("APRIORI: Algorithm for Mining Association Rules")
@Description("Searches for frequent itemsets")
@Reference(authors = "R. Agrawal, R. Srikant", title = "Fast Algorithms for Mining Association Rules in Large Databases", booktitle = "Proc. 20th Int. Conf. on Very Large Data Bases (VLDB '94), Santiago de Chile, Chile 1994", url = "http://www.acm.org/sigmod/vldb/conf/1994/P487.PDF")
public class APRIORI extends AbstractAlgorithm<AprioriResult> {
  /**
   * The logger for this class.
   */
  private static final Logging LOG = Logging.getLogger(APRIORI.class);

  /**
   * Optional parameter to specify the threshold for minimum frequency, must be
   * a double greater than or equal to 0 and less than or equal to 1.
   * Alternatively to parameter {@link #MINSUPP_ID}).
   */
  public static final OptionID MINFREQ_ID = new OptionID("apriori.minfreq", "Threshold for minimum frequency as percentage value " + "(alternatively to parameter apriori.minsupp).");

  /**
   * Parameter to specify the threshold for minimum support as minimally
   * required number of transactions, must be an integer equal to or greater
   * than 0. Alternatively to parameter {@link #MINFREQ_ID} - setting
   * {@link #MINSUPP_ID} is slightly preferable over setting {@link #MINFREQ_ID}
   * in terms of efficiency.
   */
  public static final OptionID MINSUPP_ID = new OptionID("apriori.minsupp", "Threshold for minimum support as minimally required number of transactions " + "(alternatively to parameter apriori.minfreq" + " - setting apriori.minsupp is slightly preferable over setting " + "apriori.minfreq in terms of efficiency).");

  /**
   * Holds the value of {@link #MINFREQ_ID}.
   */
  private double minfreq = Double.NaN;

  /**
   * Holds the value of {@link #MINSUPP_ID}.
   */
  private int minsupp = Integer.MIN_VALUE;

  /**
   * Constructor with minimum frequency.
   * 
   * @param minfreq Minimum frequency
   */
  public APRIORI(double minfreq) {
    super();
    this.minfreq = minfreq;
  }

  /**
   * Constructor with minimum support.
   * 
   * @param minsupp Minimum support
   */
  public APRIORI(int minsupp) {
    super();
    this.minsupp = minsupp;
  }

  /**
   * Performs the APRIORI algorithm on the given database.
   * 
   * @param relation the Relation to process
   * @return the AprioriResult learned by this APRIORI
   */
  public AprioriResult run(Relation<BitVector> relation) {
    List<Itemset> solution = new ArrayList<>();
    final int size = relation.size();
    // TODO: we don't strictly require a vector field.
    // We could work with knowing just the maximum dimensionality beforehand.
    VectorFieldTypeInformation<BitVector> meta = RelationUtil.assumeVectorField(relation);
    if(size > 0) {
      final int dim = meta.getDimensionality();
      Duration timeone = LOG.newDuration("apriori.1-items.time");
      timeone.begin();
      List<OneItemset> oneitems = buildFrequentOneItemsets(relation, dim);
      timeone.end();
      LOG.statistics(timeone);
      if(LOG.isVerbose()) {
        StringBuilder msg = new StringBuilder();
        msg.append("1-frequentItemsets (").append(oneitems.size()).append(")");
        if(LOG.isDebuggingFine()) {
          debugDumpCandidates(msg, oneitems, meta);
        }
        msg.append('\n');
        LOG.verbose(msg);
      }
      solution.addAll(oneitems);
      if(oneitems.size() >= 2) {
        Duration timetwo = LOG.newDuration("apriori.2-items.time");
        timetwo.begin();
        List<? extends Itemset> candidates = buildFrequentTwoItemsets(oneitems, relation, dim);
        timetwo.end();
        LOG.statistics(timetwo);
        if(LOG.isVerbose()) {
          StringBuilder msg = new StringBuilder();
          msg.append("2-frequentItemsets (").append(candidates.size()).append(")");
          if(LOG.isDebuggingFine()) {
            debugDumpCandidates(msg, candidates, meta);
          }
          msg.append('\n');
          LOG.verbose(msg);
        }
        solution.addAll(candidates);
        for(int length = 3; candidates.size() >= length; length++) {
          StringBuilder msg = LOG.isVerbose() ? new StringBuilder() : null;
          Duration timel = LOG.newDuration("apriori." + length + "-items.time");
          // Join to get the new candidates
          timel.begin();
          candidates = aprioriGenerate(candidates, length, dim);
          if(msg != null) {
            if(length > 2 && LOG.isDebuggingFinest()) {
              msg.append(length).append("-candidates after pruning (").append(candidates.size()).append(")");
              debugDumpCandidates(msg, candidates, meta);
            }
          }
          candidates = frequentItemsets(candidates, relation);
          timel.end();
          LOG.statistics(timel);
          if(msg != null) {
            msg.append(length).append("-frequentItemsets (").append(candidates.size()).append(")");
            if(LOG.isDebuggingFine()) {
              debugDumpCandidates(msg, candidates, meta);
            }
            LOG.verbose(msg.toString());
          }
          solution.addAll(candidates);
        }
      }
    }
    return new AprioriResult("APRIORI", "apriori", solution, meta);
  }

  /**
   * Build the 1-itemsets.
   * 
   * @param relation Data relation
   * @param dim Maximum dimensionality
   * @return 1-itemsets
   */
  protected List<OneItemset> buildFrequentOneItemsets(final Relation<BitVector> relation, final int dim) {
    final int needed = (minfreq >= 0.) ? (int) Math.ceil(minfreq * relation.size()) : minsupp;
    // TODO: use TIntList and prefill appropriately to avoid knowing "dim"
    // beforehand?
    int[] counts = new int[dim];
    for(DBIDIter iditer = relation.iterDBIDs(); iditer.valid(); iditer.advance()) {
      BitVector bv = relation.get(iditer);
      long[] bits = bv.getBits();
      for(int i = BitsUtil.nextSetBit(bits, 0); i >= 0; i = BitsUtil.nextSetBit(bits, i + 1)) {
        counts[i]++;
      }
    }
    // Generate initial candidates of length 1.
    List<OneItemset> candidates = new ArrayList<>(dim);
    for(int i = 0; i < dim; i++) {
      if(counts[i] >= needed) {
        candidates.add(new OneItemset(i, counts[i]));
      }
    }
    return candidates;
  }

  /**
   * Build the 2-itemsets.
   * 
   * @param oneitems Frequent 1-itemsets
   * @param relation Data relation
   * @param dim Maximum dimensionality
   * @return Frequent 2-itemsets
   */
  protected List<SparseItemset> buildFrequentTwoItemsets(List<OneItemset> oneitems, final Relation<BitVector> relation, final int dim) {
    final int needed = (minfreq >= 0.) ? (int) Math.ceil(minfreq * relation.size()) : minsupp;
    int f1 = 0;
    long[] mask = BitsUtil.zero(dim);
    for(OneItemset supported : oneitems) {
      BitsUtil.setI(mask, supported.item);
      f1++;
    }
    // We quite aggressively size the map, assuming that almost each combination
    // is present somewhere. If this won't fit into memory, we're likely running
    // OOM somewhere later anyway!
    TLongIntHashMap map = new TLongIntHashMap((f1 * (f1 - 1)) >> 1);
    final long[] scratch = BitsUtil.zero(dim);
    for(DBIDIter iditer = relation.iterDBIDs(); iditer.valid(); iditer.advance()) {
      long[] bv = relation.get(iditer).getBits();
      for(int i = 0; i < scratch.length; i++) {
        scratch[i] = (i < bv.length) ? (mask[i] & bv[i]) : 0L;
      }
      for(int i = BitsUtil.nextSetBit(scratch, 0); i >= 0; i = BitsUtil.nextSetBit(scratch, i + 1)) {
        for(int j = BitsUtil.nextSetBit(scratch, i + 1); j >= 0; j = BitsUtil.nextSetBit(scratch, j + 1)) {
          long key = (((long) i) << 32) | j;
          map.put(key, 1 + map.get(key));
        }
      }
    }
    // Generate candidates of length 2.
    List<SparseItemset> candidates = new ArrayList<>(f1 * (int) Math.sqrt(f1));
    for(TLongIntIterator iter = map.iterator(); iter.hasNext();) {
      iter.advance(); // Trove style iterator - advance first.
      if(iter.value() >= needed) {
        int ii = (int) (iter.key() >>> 32);
        int ij = (int) (iter.key() & -1L);
        candidates.add(new SparseItemset(new int[] { ii, ij }, iter.value()));
      }
    }
    // The hashmap may produce them out of order.
    Collections.sort(candidates);
    return candidates;
  }

  /**
   * Returns the frequent BitSets out of the given BitSets with respect to the
   * given database.
   * 
   * @param support Support map.
   * @param candidates the candidates to be evaluated
   * @param relation the database to evaluate the candidates on
   * @param mask Bitmask of items that were 1-frequent
   * @return Itemsets with sufficient support
   */
  protected List<? extends Itemset> frequentItemsets(List<? extends Itemset> candidates, Relation<BitVector> relation) {
    final int needed = (minfreq >= 0.) ? (int) Math.ceil(minfreq * relation.size()) : minsupp;
    for(DBIDIter iditer = relation.iterDBIDs(); iditer.valid(); iditer.advance()) {
      BitVector bv = relation.get(iditer);
      // TODO can we exploit that the candidate set it sorted?
      for(Itemset candidate : candidates) {
        if(candidate.containedIn(bv)) {
          candidate.increaseSupport();
        }
      }
    }
    // Retain only those with minimum support:
    List<Itemset> supported = new ArrayList<>(candidates.size());
    for(Iterator<? extends Itemset> iter = candidates.iterator(); iter.hasNext();) {
      final Itemset candidate = iter.next();
      if(candidate.getSupport() >= needed) {
        supported.add(candidate);
      }
    }
    return supported;
  }

  /**
   * Prunes a given set of candidates to keep only those BitSets where all
   * subsets of bits flipping one bit are frequent already.
   * 
   * @param supported Support map
   * @param length Itemset length
   * @param dim Dimensionality
   * @return itemsets that cannot be pruned by apriori
   */
  protected List<Itemset> aprioriGenerate(List<? extends Itemset> supported, int length, int dim) {
    List<Itemset> candidateList = new ArrayList<>();
    if(supported.size() <= 0) {
      return candidateList;
    }
    Itemset ref = supported.get(0);
    if(ref instanceof SparseItemset) {
      // TODO: we currently never switch to DenseItemSet. This may however be
      // beneficial when we have few dimensions and many candidates.
      // E.g. when length > 32 and dim < 100. But this needs benchmarking!
      // For length < 5 and dim > 3000, SparseItemset unsurprisingly was faster

      // Scratch item to use for searching.
      SparseItemset scratch = new SparseItemset(new int[length - 1]);

      final int ssize = supported.size();
      for(int i = 0; i < ssize; i++) {
        SparseItemset ii = (SparseItemset) supported.get(i);
        prefix: for(int j = i + 1; j < ssize; j++) {
          SparseItemset ij = (SparseItemset) supported.get(j);
          if(!ii.prefixTest(ij)) {
            break prefix; // Prefix doesn't match
          }
          // Test subsets (re-) using scratch object
          System.arraycopy(ii.indices, 1, scratch.indices, 0, length - 2);
          scratch.indices[length - 2] = ij.indices[length - 2];
          for(int k = length - 3; k >= 0; k--) {
            scratch.indices[k] = ii.indices[k + 1];
            int pos = Collections.binarySearch(supported, scratch);
            if(pos < 0) {
              // Prefix was okay, but one other subset was not frequent
              continue prefix;
            }
          }
          int[] items = new int[length];
          System.arraycopy(ii.indices, 0, items, 0, length - 1);
          items[length - 1] = ij.indices[length - 2];
          candidateList.add(new SparseItemset(items));
        }
      }
      return candidateList;
    }
    if(ref instanceof DenseItemset) {
      // Scratch item to use for searching.
      DenseItemset scratch = new DenseItemset(BitsUtil.zero(dim), length - 1);

      final int ssize = supported.size();
      for(int i = 0; i < ssize; i++) {
        DenseItemset ii = (DenseItemset) supported.get(i);
        prefix: for(int j = i + 1; j < ssize; j++) {
          DenseItemset ij = (DenseItemset) supported.get(j);
          // Prefix test via "|i1 ^ i2| = 2"
          System.arraycopy(ii.items, 0, scratch.items, 0, ii.items.length);
          BitsUtil.xorI(scratch.items, ij.items);
          if(BitsUtil.cardinality(scratch.items) != 2) {
            break prefix; // No prefix match; since sorted, no more can follow!
          }
          // Ensure that the first difference is the last item in ii:
          int first = BitsUtil.nextSetBit(scratch.items, 0);
          if(BitsUtil.nextSetBit(ii.items, first + 1) > -1) {
            break prefix; // Different overlap by chance?
          }
          BitsUtil.orI(scratch.items, ij.items);

          // Test subsets.
          for(int l = length, b = BitsUtil.nextSetBit(scratch.items, 0); l > 2; l--, b = BitsUtil.nextSetBit(scratch.items, b + 1)) {
            BitsUtil.clearI(scratch.items, b);
            int pos = Collections.binarySearch(supported, scratch);
            if(pos < 0) {
              continue prefix;
            }
            BitsUtil.setI(scratch.items, b);
          }
          candidateList.add(new DenseItemset(scratch.items.clone(), length));
        }
      }
      return candidateList;
    }
    throw new AbortException("Unexpected itemset type " + ref.getClass());
  }

  /**
   * Debug method: output all itemsets.
   * 
   * @param msg Output buffer
   * @param candidates Itemsets to dump
   * @param meta Metadata for item labels
   */
  private void debugDumpCandidates(StringBuilder msg, List<? extends Itemset> candidates, VectorFieldTypeInformation<BitVector> meta) {
    msg.append(':');
    for(Itemset itemset : candidates) {
      msg.append(" [");
      itemset.appendTo(msg, meta);
      msg.append(']');
    }
  }

  @Override
  public TypeInformation[] getInputTypeRestriction() {
    return TypeUtil.array(TypeUtil.BIT_VECTOR_FIELD);
  }

  @Override
  protected Logging getLogger() {
    return LOG;
  }

  /**
   * Parameterization class.
   * 
   * @author Erich Schubert
   * 
   * @apiviz.exclude
   */
  public static class Parameterizer extends AbstractParameterizer {
    /**
     * Parameter for minFreq.
     */
    protected Double minfreq = null;

    /**
     * Parameter for minSupp.
     */
    protected Integer minsupp = null;

    @Override
    protected void makeOptions(Parameterization config) {
      super.makeOptions(config);
      DoubleParameter minfreqP = new DoubleParameter(MINFREQ_ID);
      minfreqP.setOptional(true);
      minfreqP.addConstraint(CommonConstraints.GREATER_EQUAL_ZERO_DOUBLE);
      minfreqP.addConstraint(CommonConstraints.LESS_EQUAL_ONE_DOUBLE);
      if(config.grab(minfreqP)) {
        minfreq = minfreqP.getValue();
      }

      IntParameter minsuppP = new IntParameter(MINSUPP_ID);
      minsuppP.setOptional(true);
      minsuppP.addConstraint(CommonConstraints.GREATER_EQUAL_ZERO_INT);
      if(config.grab(minsuppP)) {
        minsupp = minsuppP.getValue();
      }

      // global parameter constraints
      config.checkConstraint(new OnlyOneIsAllowedToBeSetGlobalConstraint(minfreqP, minsuppP));
      config.checkConstraint(new OneMustBeSetGlobalConstraint(minfreqP, minsuppP));
    }

    @Override
    protected APRIORI makeInstance() {
      if(minfreq != null) {
        return new APRIORI(minfreq);
      }
      if(minsupp != null) {
        return new APRIORI(minsupp);
      }
      return null;
    }
  }
}