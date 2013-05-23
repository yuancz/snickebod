package ch.ethz.nlp.headline.learning;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.ethz.nlp.headline.Dataset;
import ch.ethz.nlp.headline.Document;
import ch.ethz.nlp.headline.Model;
import ch.ethz.nlp.headline.Task;
import ch.ethz.nlp.headline.duc2004.Duc2004Dataset;
import ch.ethz.nlp.headline.preprocessing.CombinedPreprocessor;
import ch.ethz.nlp.headline.preprocessing.ContentPreprocessor;
import ch.ethz.nlp.headline.selection.SentenceFeatureExtractor;
import ch.ethz.nlp.headline.selection.SentenceScore;
import ch.ethz.nlp.headline.selection.TfIdfProvider;
import ch.ethz.nlp.headline.util.CoreNLPUtil;
import ch.ethz.nlp.headline.util.RougeN;

import com.google.common.collect.ImmutableList;

import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.ArrayUtils;
import edu.stanford.nlp.util.CoreMap;

public class FeatureWeightRegression {

	private static final Logger LOG = LoggerFactory
			.getLogger(FeatureWeightRegression.class);

	private final RougeN rouge = new RougeN(1);
	private final TfIdfProvider tfIdfProvider;

	private final ContentPreprocessor preprocessor;

	public FeatureWeightRegression(TfIdfProvider tfIdfProvider) {
		this.tfIdfProvider = tfIdfProvider;
		this.preprocessor = CombinedPreprocessor.all();
	}

	public void analyse(List<Task> tasks) {
		// for every sentence (data point), there is a feature vector (in the
		// sentence score)
		List<SentenceScore> featureValues = new ArrayList<>();
		// for every sentence, we keep its ROUGE-1 recall
		List<Double> rougeValues = new ArrayList<>();
		
		for (Task task : tasks) {
			Document document = task.getDocument();
			String documentContent = document.getContent();
			documentContent = preprocessor.preprocess(documentContent);

			List<Annotation> models = new ArrayList<>();
			for (Model model : task.getModels()) {
				String modelContent = model.getContent();
				models.add(getAnnotation(modelContent));
			}

			Annotation documentAnnotation = getAnnotation(documentContent);
			SentenceFeatureExtractor extractor = new SentenceFeatureExtractor(
					tfIdfProvider, documentAnnotation);
			List<CoreMap> sentences = documentAnnotation
					.get(SentencesAnnotation.class);
			for (int pos = 0; pos < sentences.size(); pos++) {
				// calculate rouge score
				CoreMap sentence = sentences.get(pos);
				Annotation sentenceAnnotation = CoreNLPUtil
						.sentencesToAnnotation(ImmutableList.of(sentence));

					double recall = rouge.compute(models, sentenceAnnotation);
					rougeValues.add(recall);
	
					// calculate sentence features
					SentenceScore features = extractor
							.extractFeaturesForSentence(sentence);
					featureValues.add(features);
			}
		}
		
		// prepare data in double arrays...
		Double[] ys = rougeValues.toArray(new Double[rougeValues.size()]);
		double[] y = ArrayUtils.toPrimitive(ys);
		double[][] X = new double[featureValues.size()][];
		for (int i = 0; i < featureValues.size(); i++) {
			SentenceScore score = featureValues.get(i);
			List<Double> vals = score.getValues();
			for (int t = 0; t < vals.size(); t++) {
				Double double1 = vals.get(t);
				if (double1.isInfinite() || double1.isNaN()) {
					throw new RuntimeException();
				}
			}
			Double[] xs = vals.toArray(new Double[vals.size()]);
			double[] x = ArrayUtils.toPrimitive(xs);
			X[i] = x;
		}
		
		LOG.info(String.format("Least squares system with %d sentences (rows) and %d features (columns)",
				y.length, X[0].length));
		
		OLSMultipleLinearRegression regression = new OLSMultipleLinearRegression();
		regression.newSampleData(y, X);
		double[] weights = regression.estimateRegressionParameters();

		StringBuilder sb = new StringBuilder();
		List<String> featureNames = featureValues.get(0).getNames();
		for (int fi = 0; fi < featureNames.size(); fi++) {
			sb.append(featureNames.get(fi));
			sb.append(" = ");
			sb.append(weights[fi+1]); // skip "offset"
			sb.append("\n");
		}
		LOG.info("Determined weights:\n " + sb.toString());
		
	}

	private Annotation getAnnotation(String content) {
		Annotation annotation = new Annotation(content);
		CoreNLPUtil.ensureLemmaAnnotation(annotation);
		return annotation;
	}

	public static void main(String[] args) {
		Dataset dataset = Duc2004Dataset.ofDefaultRoot();
		List<Task> tasks = dataset.getTasks();
		TfIdfProvider tfIdfProvider = TfIdfProvider.of(dataset);
		FeatureWeightRegression regression = new FeatureWeightRegression(
				tfIdfProvider);
		regression.analyse(tasks);
	}

}