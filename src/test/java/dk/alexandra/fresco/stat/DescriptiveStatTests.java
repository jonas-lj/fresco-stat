package dk.alexandra.fresco.stat;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import dk.alexandra.fresco.framework.Application;
import dk.alexandra.fresco.framework.DRes;
import dk.alexandra.fresco.framework.TestThreadRunner.TestThread;
import dk.alexandra.fresco.framework.TestThreadRunner.TestThreadFactory;
import dk.alexandra.fresco.framework.builder.numeric.ProtocolBuilderNumeric;
import dk.alexandra.fresco.framework.sce.resources.ResourcePool;
import dk.alexandra.fresco.framework.util.Pair;
import dk.alexandra.fresco.framework.value.SInt;
import dk.alexandra.fresco.lib.collections.Matrix;
import dk.alexandra.fresco.lib.real.SReal;
import dk.alexandra.fresco.stat.descriptive.LeakyBreakTies;
import dk.alexandra.fresco.stat.descriptive.LeakyFrequencyTable;
import dk.alexandra.fresco.stat.descriptive.Ranks;
import dk.alexandra.fresco.stat.utils.MatrixUtils;
import dk.alexandra.fresco.stat.utils.sort.FindTiedGroups;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.apache.commons.math3.stat.ranking.NaturalRanking;
import org.apache.commons.math3.stat.ranking.RankingAlgorithm;

public class DescriptiveStatTests {

  public static List<List<Integer>> ranksDataset() {

    // Data from Blæsild & Granfeldt
    List<Integer> data1 = List
        .of(200, 215, 225, 229, 230, 232, 241, 253, 256, 264, 268, 288, 288);
    List<Integer> data2 = List
        .of(163, 182, 188, 195, 202, 205, 212, 214, 215, 230, 235, 255, 272);
    List<Integer> data3 = List
        .of(268, 271, 273, 282, 285, 299, 309, 310, 314, 320, 337, 340, 345);
    List<Integer> data4 = List
        .of(201, 216, 241, 257, 259, 267, 269, 282, 283, 291, 291, 312, 326);
    return List.of(data1, data2, data3, data4);
  }

  public static class TestCorrelation<ResourcePoolT extends ResourcePool>
      extends TestThreadFactory<ResourcePoolT, ProtocolBuilderNumeric> {

    @Override
    public TestThread<ResourcePoolT, ProtocolBuilderNumeric> next() {
      return new TestThread<>() {

        final List<Double> x = Arrays.asList(1.0, 2.0, 3.0, 4.0, 5.0);
        final List<Double> y = Arrays.asList(1.0, 2.0, 1.3, 3.75, 2.25);

        @Override
        public void test() throws Exception {

          Application<BigDecimal, ProtocolBuilderNumeric> testApplication = builder -> {
            List<DRes<SReal>> xSecret =
                x.stream().map(x -> builder.realNumeric().input(x, 1)).collect(Collectors.toList());
            List<DRes<SReal>> ySecret =
                y.stream().map(y -> builder.realNumeric().input(y, 2)).collect(Collectors.toList());
            DRes<SReal> r = Statistics.using(builder).correlation(xSecret, ySecret);
            return builder.realNumeric().open(r);
          };

          double[] xArray = x.stream().mapToDouble(i -> i).toArray();
          double[] yArray = y.stream().mapToDouble(i -> i).toArray();

          PearsonsCorrelation correlation = new PearsonsCorrelation();
          double expected = correlation.correlation(xArray, yArray);

          BigDecimal output = runApplication(testApplication);
          System.out.println(output + " ~ " + expected);
          assertTrue(Math.abs(expected - output.doubleValue()) < 0.01);
        }
      };
    }
  }


  public static class TestHistogramInt<ResourcePoolT extends ResourcePool>
      extends TestThreadFactory<ResourcePoolT, ProtocolBuilderNumeric> {

    @Override
    public TestThread<ResourcePoolT, ProtocolBuilderNumeric> next() {
      return new TestThread<>() {

        final List<Integer> x = Arrays.asList(1, 5, 7, 3, 9, 5, 34, 5, -1, -3);
        final List<Integer> buckets = Arrays.asList(0, 5, 10);
        final List<Integer> expected = Arrays.asList(2, 5, 2, 1);

        @Override
        public void test() throws Exception {

          Application<List<BigInteger>, ProtocolBuilderNumeric> testApplication = builder -> {
            return builder.seq(seq -> {
              List<DRes<SInt>> xSecret =
                  x.stream().map(x -> seq.numeric().input(x, 1)).collect(Collectors.toList());
              List<DRes<SInt>> bSecret =
                  buckets.stream().map(b -> seq.numeric().input(b, 2)).collect(Collectors.toList());
              DRes<List<DRes<SInt>>> h = Statistics.using(seq).histogramInt(bSecret, xSecret);
              return h;
            }).seq((seq, h) -> {
              List<DRes<BigInteger>> out =
                  h.stream().map(seq.numeric()::open).collect(Collectors.toList());
              return () -> out.stream().map(DRes::out).collect(Collectors.toList());
            });
          };

          List<BigInteger> output = runApplication(testApplication);
          for (int i = 0; i < output.size(); i++) {
            assertEquals(expected.get(i).intValue(), output.get(i).intValue());
          }
        }
      };
    }
  }

  public static class TestHistogramFixed<ResourcePoolT extends ResourcePool>
      extends TestThreadFactory<ResourcePoolT, ProtocolBuilderNumeric> {

    @Override
    public TestThread<ResourcePoolT, ProtocolBuilderNumeric> next() {
      return new TestThread<>() {

        final List<Double> x = Arrays.asList(.1, .5, .7, .3, .9, .5, 3.4, .5, -.1, -.3);
        final List<Double> buckets = Arrays.asList(.0, .5, 1.0);
        final List<Integer> expected = Arrays.asList(2, 5, 2, 1);

        @Override
        public void test() throws Exception {

          Application<List<BigInteger>, ProtocolBuilderNumeric> testApplication = builder -> {
            return builder.seq(seq -> {
              List<DRes<SReal>> xSecret =
                  x.stream().map(x -> seq.realNumeric().input(x, 1)).collect(Collectors.toList());
              List<DRes<SReal>> bSecret = buckets.stream().map(b -> seq.realNumeric().input(b, 2))
                  .collect(Collectors.toList());
              DRes<List<DRes<SInt>>> h = Statistics.using(seq).histogramReal(bSecret, xSecret);
              return h;
            }).seq((seq, h) -> {
              List<DRes<BigInteger>> out =
                  h.stream().map(seq.numeric()::open).collect(Collectors.toList());
              return () -> out.stream().map(DRes::out).collect(Collectors.toList());
            });
          };

          List<BigInteger> output = runApplication(testApplication);
          for (int i = 0; i < output.size(); i++) {
            assertEquals(expected.get(i).intValue(), output.get(i).intValue());
          }
        }
      };
    }
  }

  public static class TestTwoDimHistogram<ResourcePoolT extends ResourcePool>
      extends TestThreadFactory<ResourcePoolT, ProtocolBuilderNumeric> {

    @Override
    public TestThread<ResourcePoolT, ProtocolBuilderNumeric> next() {
      return new TestThread<>() {

        final List<Integer> x = Arrays.asList(1, 3, 5, 6, 7, 8);
        final List<Integer> y = Arrays.asList(2, 4, 5, 8, 9, 10);
        final List<Integer> bucketsX = Arrays.asList(1, 4, 9);
        final List<Integer> bucketsY = Arrays.asList(1, 4, 9);

        @Override
        public void test() throws Exception {

          Application<Matrix<BigInteger>, ProtocolBuilderNumeric> testApplication = builder -> {
            return builder.seq(seq -> {
              Pair<List<DRes<SInt>>, List<DRes<SInt>>> buckets = new Pair<>(
                  bucketsX.stream().map(x -> seq.numeric().input(x, 1))
                      .collect(Collectors.toList()),
                  bucketsY.stream().map(x -> seq.numeric().input(x, 1)).collect(Collectors.toList())
              );
              List<Pair<DRes<SInt>, DRes<SInt>>> data = IntStream.range(0, x.size()).mapToObj(
                  i -> new Pair<>(seq.numeric().input(x.get(i), 1),
                      seq.numeric().input(y.get(i), 1))).collect(Collectors.toList());
              x.stream().map(x -> seq.realNumeric().input(x, 1)).collect(Collectors.toList());

              DRes<Matrix<DRes<SInt>>> histogram = Statistics.using(seq)
                  .twoDimensionalHistogramInt(buckets, data);
              return histogram;
            }).seq((seq, histogram) -> {
              Matrix<DRes<BigInteger>> opened = MatrixUtils.map(histogram, seq.numeric()::open);
              return () -> MatrixUtils.map(opened, DRes::out);
            });
          };

          Matrix<BigInteger> output = runApplication(testApplication);
          assertEquals(BigInteger.valueOf(0), output.getRow(0).get(0));
          assertEquals(BigInteger.valueOf(1), output.getRow(1).get(1));
          assertEquals(BigInteger.valueOf(3), output.getRow(2).get(2));
        }
      };
    }
  }

  public static class TestLeakyFrequencyTable<ResourcePoolT extends ResourcePool>
      extends TestThreadFactory<ResourcePoolT, ProtocolBuilderNumeric> {

    @Override
    public TestThread<ResourcePoolT, ProtocolBuilderNumeric> next() {
      return new TestThread<>() {
        final List<Integer> x = Arrays.asList(1, 3, 2, 1, 3, 1);

        @Override
        public void test() {
          Application<List<Pair<BigInteger, Integer>>, ProtocolBuilderNumeric> testApplication = builder ->
              builder.seq(seq -> {
                List<DRes<SInt>> xSecret =
                    x.stream().map(x -> seq.numeric().input(x, 1)).collect(Collectors.toList());
                DRes<List<Pair<DRes<SInt>, Integer>>> frequencyTable = new LeakyFrequencyTable(
                    xSecret).buildComputation(seq);
                return frequencyTable;
              }).seq((seq, ft) -> {
                List<Pair<DRes<BigInteger>, Integer>> out =
                    ft.stream()
                        .map(p -> new Pair<>(seq.numeric().open(p.getFirst()), p.getSecond()))
                        .collect(Collectors.toList());
                return () -> out.stream().map(p -> new Pair<>(p.getFirst().out(), p.getSecond()))
                    .collect(Collectors.toList());
              });

          Map<Integer, Integer> expected = new HashMap<>();
          for (int xi : x) {
            expected.putIfAbsent(xi, 0);
            expected.computeIfPresent(xi, (k, v) -> v + 1);
          }

          List<Pair<BigInteger, Integer>> output = runApplication(testApplication);
          for (int i = 0; i < output.size(); i++) {
            assertEquals(expected.get(output.get(i).getFirst().intValue()),
                output.get(i).getSecond());
          }
        }
      };
    }
  }

  public static class TestLeakyRanks<ResourcePoolT extends ResourcePool>
      extends TestThreadFactory<ResourcePoolT, ProtocolBuilderNumeric> {

    @Override
    public TestThread<ResourcePoolT, ProtocolBuilderNumeric> next() {
      return new TestThread<>() {

        final List<Integer> data = ranksDataset().stream().flatMap(List::stream)
            .collect(Collectors.toList());

        @Override
        public void test() throws Exception {
          data.sort(Integer::compareTo);

          Application<List<Double>, ProtocolBuilderNumeric> testApplication = builder -> {
            List<DRes<SInt>> input = data.stream().map(x -> builder.numeric().input(x, 1))
                .collect(Collectors.toList());

            return new LeakyBreakTies(input).buildComputation(builder);
          };

          List<Double> output = runApplication(testApplication);

          RankingAlgorithm ranking = new NaturalRanking();

          double[] rank = ranking.rank(data.stream().mapToDouble(Double::valueOf).toArray());

          assertArrayEquals(rank, output.stream().mapToDouble(x -> x).toArray(), 0.01);
        }
      };
    }
  }

  public static class TestRanks<ResourcePoolT extends ResourcePool>
      extends TestThreadFactory<ResourcePoolT, ProtocolBuilderNumeric> {

    @Override
    public TestThread<ResourcePoolT, ProtocolBuilderNumeric> next() {
      return new TestThread<>() {

        final List<List<Integer>> data = ranksDataset();

        @Override
        public void test() throws Exception {

          Application<Pair<List<BigDecimal>, Double>, ProtocolBuilderNumeric> testApplication = builder -> builder
              .seq(seq -> {
                List<List<DRes<SInt>>> input = data.stream().map(
                    sample -> sample.stream().map(x -> seq.numeric().input(x, 1))
                        .collect(Collectors.toList())).collect(Collectors.toList());
                DRes<Pair<List<DRes<SReal>>, Double>> ranks = new Ranks(input, true)
                    .buildComputation(seq);
                return ranks;
              }).seq((seq, ranks) -> {
                List<DRes<BigDecimal>> openList =
                    ranks.getFirst().stream().map(seq.realNumeric()::open)
                        .collect(Collectors.toList());
                return () -> new Pair<>(
                    openList.stream().map(DRes::out).collect(Collectors.toList()),
                    ranks.getSecond());
              });

          Pair<List<BigDecimal>, Double> output = runApplication(testApplication);

          // Data and expected values from example 12.3 in Blæsild & Granfeldt: "Statistics with
          // applications in biology and geology".
          assertArrayEquals(new double[]{282, 147, 549, 400},
              output.getFirst().stream().mapToDouble(BigDecimal::doubleValue).toArray(), 0.01);
          assertEquals(1.000282292212767, output.getSecond(), 0.01);
        }
      };
    }
  }

  public static class TestTiedGroups<ResourcePoolT extends ResourcePool>
      extends TestThreadFactory<ResourcePoolT, ProtocolBuilderNumeric> {

    @Override
    public TestThread<ResourcePoolT, ProtocolBuilderNumeric> next() {
      return new TestThread<>() {

        final List<Integer> data = List.of(2, 5, 3, 6, 1, 3, 7, 6, 3, 9, 8, 7, 5, 5);

        @Override
        public void test() throws Exception {
          Application<List<BigInteger>, ProtocolBuilderNumeric> testApplication = builder -> {
            List<DRes<SInt>> input = data.stream().map(x -> builder.numeric().input(x, 1))
                .collect(Collectors.toList());

            return new FindTiedGroups(input).buildComputation(builder);
          };

          List<BigInteger> output = runApplication(testApplication);

          assertNotEquals(output.get(1), output.get(7));
          assertNotEquals(output.get(1), output.get(11));
          assertEquals(output.get(1), output.get(12));
          assertEquals(output.get(1), output.get(13));

          assertNotEquals(output.get(2), output.get(4));
          assertEquals(output.get(2), output.get(5));
          assertEquals(output.get(2), output.get(8));
        }
      };
    }
  }

}
