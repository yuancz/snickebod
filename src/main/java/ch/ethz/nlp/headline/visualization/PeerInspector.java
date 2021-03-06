package ch.ethz.nlp.headline.visualization;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.ethz.nlp.headline.Model;
import ch.ethz.nlp.headline.Peer;
import ch.ethz.nlp.headline.Task;
import ch.ethz.nlp.headline.cache.AnnotationProvider;
import ch.ethz.nlp.headline.util.RougeN;
import edu.stanford.nlp.pipeline.Annotation;

public class PeerInspector {

	private static final Logger LOG = LoggerFactory
			.getLogger(PeerInspector.class);

	private static final double ROUGE_1_THRESHOLD = 0.20;
	private static final double ROUGE_2_THRESHOLD = 0.05;

	private static final AnsiColor MODEL_COLOR = AnsiColor.BLUE;

	private final AnnotationProvider annotationProvider;

	public PeerInspector(AnnotationProvider annotationProvider) {
		super();
		this.annotationProvider = annotationProvider;
	}

	public void inspect(Task task, Collection<Peer> peers) throws IOException {
		NGramHitVisualizer visualizer = NGramHitVisualizer.of(
				annotationProvider, task.getModels());
		List<Annotation> modelAnnotations = visualizer.getModelAnnotations();

		for (Model model : task.getModels()) {
			String content = model.getContent();
			String logString = String.format("%-16s%s", "MODEL", content);
			LOG.info(MODEL_COLOR.makeString(logString));
		}
		
		RougeN rouge1 = new RougeN(modelAnnotations, 1);
		RougeN rouge2 = new RougeN(modelAnnotations, 2);

		for (Peer peer : peers) {
			String generatorId = peer.getGeneratorId();
			String headline = peer.load();

			Annotation annotation = annotationProvider.getAnnotation(headline);
			String visualization = visualizer.visualize(annotation);
			visualization = visualization.replaceAll("\n", " ");

			double rouge1Recall = rouge1.compute(annotation);
			double rouge2Recall = rouge2.compute(annotation);
			String rouge1String = String.format("%.2f", rouge1Recall)
					.substring(1);
			String rouge2String = String.format("%.2f", rouge2Recall)
					.substring(1);

			if (!generatorId.equals("BASE")) {
				if (rouge1Recall < ROUGE_1_THRESHOLD) {
					rouge1String = AnsiColor.RED.makeString(rouge1String);
				}

				if (rouge2Recall < ROUGE_2_THRESHOLD) {
					rouge2String = AnsiColor.RED.makeString(rouge2String);
				}
			}

			LOG.info(String.format("%-8s%s %s %s", generatorId, rouge1String,
					rouge2String, visualization));
		}
	}

}
