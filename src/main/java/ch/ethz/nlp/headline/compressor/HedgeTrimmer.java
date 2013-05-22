package ch.ethz.nlp.headline.compressor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Stack;

import com.google.common.collect.ImmutableSet;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Filter;

public class HedgeTrimmer extends TreeCompressor {

	private static final Set<String> TRIMMED_LEMMAS = ImmutableSet.of("a",
			"the", "have", "be", "its", "here");

	@Override
	public Tree compress(Tree tree, CoreMap sentence) {
		tree = getLowestLeftmostSWithNPVP(tree);
		tree = removeLowContentNodes(tree);
		// TODO: Currently not used because it makes the performance worse
		// tree = shortenIterativelyRule1(tree);
		tree = shortenIterativelyRule2(tree);

		return tree;
	}

	private Tree getLowestLeftmostSWithNPVP(Tree tree) {
		Tree newTree = getLowestLeftmostSWithNPVPImpl(tree);
		if (newTree == null) {
			// Account for cases where the first sentence is only a fragment
			newTree = tree;
		}
		return newTree;
	}

	private Tree getLowestLeftmostSWithNPVPImpl(Tree tree) {
		Tree result = null;
		if (tree.value().equals("S")) {
			boolean hasNPChild = false;
			boolean hasVPChild = false;
			for (Tree child : tree.children()) {
				if (child.value().equals("NP")) {
					hasNPChild = true;
				} else if (child.value().equals("VP")) {
					hasVPChild = true;
				}
				if (hasNPChild && hasVPChild) {
					break;
				}
			}
			if (hasNPChild && hasVPChild) {
				result = tree;
			}
		}
		// Only consider the left-most child
		Tree firstChild = tree.firstChild();
		if (firstChild != null) {
			Tree childResult = getLowestLeftmostSWithNPVPImpl(firstChild);
			if (childResult != null) {
				result = childResult;
			}
		}

		return result;
	}

	private Tree removeLowContentNodes(Tree tree) {
		tree = tree.prune(new Filter<Tree>() {

			private static final long serialVersionUID = 1L;

			@Override
			public boolean accept(Tree tree) {
				CoreLabel label = (CoreLabel) tree.label();
				String lemma = label.lemma();
				if (TRIMMED_LEMMAS.contains(lemma)) {
					return false;
				}
				return true;
			}

		});

		tree = tree.prune(new Filter<Tree>() {

			private static final long serialVersionUID = 1L;

			@Override
			public boolean accept(Tree tree) {
				// Remove [PP … [NNP [X] …] …] where X is a tagged as part of a
				// time expression
				if (tree.value().equals("PP")) {
					// Only allow at most 3 leaves to be removed. Otherwise,
					// large parts of the sentence might be trimmed by accident.
					if (tree.getLeaves().size() <= 3) {
						Stack<Tree> treeStack = new Stack<>();
						treeStack.addAll(Arrays.asList(tree.children()));
						while (!treeStack.isEmpty()) {
							Tree child = treeStack.pop();
							if (!child.value().equals("PP")) {
								CoreLabel childLabel = (CoreLabel) child
										.label();
								String ner = childLabel.ner();
								if (Objects.equals(ner, "DATE")) {
									logTrimming(tree, "Date");
									return false;
								} else {
									treeStack.addAll(Arrays.asList(child
											.children()));
								}
							}
						}
					}
				}
				// Remove [NNP [X]] where X is a tagged as part of a
				// time expression
				if (tree.value().equals("NNP")) {
					CoreLabel childLabel = (CoreLabel) tree.firstChild()
							.label();
					String ner = childLabel.ner();
					if (Objects.equals(ner, "DATE")) {
						logTrimming(tree, "Date");
						return false;
					}
				}

				return true;
			}

		});

		return tree;
	}

	@SuppressWarnings("unused")
	private Tree shortenIterativelyRule1(Tree fullTree) {
		List<CoreLabel> candidates = new ArrayList<>();
		Set<String> XP = ImmutableSet.of("NP", "VP", "S");

		for (Tree tree : fullTree) {
			if (XP.contains(tree.value())) {
				Tree[] children = tree.children();
				if (children[0].value().equals(tree.value())) {
					for (int i = 1; i < children.length; i++) {
						candidates.add((CoreLabel) children[i].label());
					}
				}
			}
		}

		if (!candidates.isEmpty()) {
			// Just eliminate the right-most, deepest candidate for the moment
			CoreLabel candidate = candidates.get(candidates.size() - 1);
			BlacklistTreeFilter treeFilter = new BlacklistTreeFilter(candidate);
			fullTree = fullTree.prune(treeFilter);

			for (Tree prunedTree : treeFilter.getPrunedTrees()) {
				logTrimming(prunedTree, "Iterative Shortening Rule 1");
			}
		}

		return fullTree;
	}

	private Tree shortenIterativelyRule2(Tree tree) {
		if (tree.value().equals("S")) {
			while (tree.firstChild() != null
					&& !tree.firstChild().value().equals("NP")) {
				logTrimming(tree.firstChild(), "Iterative Shortening Rule 2");
				tree.removeChild(0);
			}
		}
		return tree;
	}

}
