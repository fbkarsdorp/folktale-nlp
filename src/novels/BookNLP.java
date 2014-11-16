package novels;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Vector;

import com.sun.org.apache.xpath.internal.operations.Quo;
import novels.annotators.CharacterAnnotator;
import novels.annotators.CharacterFeatureAnnotator;
import novels.annotators.CoreferenceAnnotator;
import novels.annotators.PhraseAnnotator;
import novels.annotators.QuotationAnnotator;
import novels.annotators.SyntaxAnnotator;
import novels.util.PrintUtil;
import novels.util.Util;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;

public class BookNLP {

	private static final String animacyFile = "files/stanford/animate.unigrams.txt";
	private static final String genderFile = "files/stanford/namegender.combine.txt";
	private static final String femaleFile = "files/stanford/female.unigrams.txt";
	private static final String maleFile = "files/stanford/male.unigrams.txt";
	private static final String corefWeights = "files/coref.weights";
	private static final String verbsOfCognitionFile = "files/verbs-of-cognition.txt";
	private static final String word2Vectors = "files/vectors.bin";


	public String weights = corefWeights;
	public String vectors = word2Vectors;

	/**
	 * Annotate a book with characters, coreference and quotations
	 * 
	 * @param book
	 */
	public void process(Book book, File outputDirectory, String outputPrefix) throws IOException {
		File charFile = new File(outputDirectory, outputPrefix + ".book");

		process(book);

		QuotationAnnotator quoteFinder = new QuotationAnnotator();
		quoteFinder.findQuotations(book);

		for (Quotation quotation : book.quotations) {
			book.tokens.get(quotation.attributionId).ner = "PERSON";
		}
		// rerun character detection with found speakers.
		process(book);

		CharacterFeatureAnnotator featureAnno = new CharacterFeatureAnnotator();
		featureAnno.annotatePaths(book);
		PrintUtil.printBookJson(book, charFile);

	}

	public void process(Book book) throws IOException {
		SyntaxAnnotator.setDependents(book);

		Dictionaries dicts = new Dictionaries();
		dicts.readAnimate(animacyFile, genderFile, maleFile, femaleFile);
		dicts.readCognitionVerbs(verbsOfCognitionFile);
		dicts.processHonorifics(book.tokens);

		CharacterAnnotator charFinder = new CharacterAnnotator();

		charFinder.findCharacters(book, dicts);
		charFinder.resolveCharacters(book, dicts, vectors);

		PhraseAnnotator phraseFinder = new PhraseAnnotator();
		phraseFinder.getPhrases(book, dicts);

		CoreferenceAnnotator coref = new CoreferenceAnnotator();
		coref.readWeights(weights);
		coref.resolvePronouns(book);
	}

	public void dumpForAnnotation(Book book, File outputDirectory, String prefix) {
		File pronounCands = new File(outputDirectory, prefix + ".pronoun.cands");
		File quotes = new File(outputDirectory, prefix + ".quote.cands");

		CoreferenceAnnotator coref = new CoreferenceAnnotator();
		HashMap<Integer, HashSet<Integer>> cands = coref.getResolvable(book);
		PrintUtil.printPronounCandidates(pronounCands, book, cands);
		PrintUtil.printQuotes(quotes, book);

	}

	public static void main(String[] args) throws Exception {

		Options options = new Options();
		options.addOption("f", false, "force processing of text file");
		options.addOption("printHTML", false, "print HTML file for inspection");
		options.addOption("w", true, "coreference weight file");
		options.addOption("doc", true, "text document to process");
		options.addOption("tok", true, "processed text document");
		options.addOption("docId", true, "text document ID to process");
		options.addOption("p", true, "output directory");
		options.addOption("id", true, "book ID");
		options.addOption("d", false, "dump pronoun and quotes for annotation");
		options.addOption("v", true, "word2vec vector file.");

		CommandLine cmd = null;
		try {
			CommandLineParser parser = new BasicParser();
			cmd = parser.parse(options, args);
		} catch (Exception e) {
			e.printStackTrace();
		}

		String outputDirectory = null;
		String prefix = "book.id";

		File input = new File(cmd.getOptionValue("doc"));
		File[] filenames = new File[1];
		if (input.isDirectory()) {
			filenames = input.listFiles();
		} else {
			filenames[0] = new File(cmd.getOptionValue("doc"));
		}

		SyntaxAnnotator syntaxAnnotator = new SyntaxAnnotator();
		for (File file: filenames) {
			File tokFile = new File(file.getName() + ".tok");
			if (!(file.getName().endsWith(".txt")) || tokFile.exists()) {
				continue;
			}
			if (!cmd.hasOption("p")) {
				System.err.println("Specify output directory with -p <directory>");
				System.exit(1);
			} else {
				outputDirectory = cmd.getOptionValue("p");
			}

			prefix = file.getName() + ".id";

			File directory = new File(outputDirectory);
			directory.mkdirs();

			String tokenFileString = null;
			tokenFileString = file.getName() + ".tok";
			File tokenDirectory = new File(tokenFileString).getParentFile();

			options.addOption("printHtml", false,
					"write HTML file with coreference links and speaker ID for inspection");

			BookNLP bookNLP = new BookNLP();
			// int docId = Integer.valueOf(cmd.getOptionValue("docId"));

			// generate or read tokens
			ArrayList<Token> tokens = null;
			File tokenFile = new File(tokenFileString);
			if (!tokenFile.exists() || cmd.hasOption("f")) {
				String doc = file.getAbsolutePath();
				String text = Util.readText(doc);
				text = Util.filterGutenberg(text);
				tokens = syntaxAnnotator.process(text);
				System.out.println("Processing text");
			} else {
				if (tokenFile.exists()) {
					System.out.println(String.format("%s exists...",
							tokenFileString));
				}
				tokens = SyntaxAnnotator.readDoc(tokenFileString);
				System.out.println("Using preprocessed tokens");
			}

			Book book = new Book(tokens);

			if (cmd.hasOption("w")) {
				bookNLP.weights = cmd.getOptionValue("w");
				System.out.println(String.format("Using coref weights: ",
						bookNLP.weights));
			} else {
				bookNLP.weights = BookNLP.corefWeights;
				System.out.println("Using default coref weights");
			}

			if (cmd.hasOption("v")) {
				bookNLP.vectors = cmd.getOptionValue("v");
				System.out.println(String.format("Using vector file: ", bookNLP.vectors));
			} else {
				bookNLP.vectors = BookNLP.word2Vectors;
				System.out.println("Using default vectors.");
			}

			book.id = prefix;
			try {
				bookNLP.process(book, directory, prefix);

				if (cmd.hasOption("printHTML")) {
					File htmlOutfile = new File(directory, prefix + ".html");
					PrintUtil.printWithLinksAndCorefAndQuotes(htmlOutfile, book);
				}

				if (cmd.hasOption("d")) {
					System.out.println("Dumping for annotation");
					bookNLP.dumpForAnnotation(book, directory, prefix);
				}

				// Print out tokens
				PrintUtil.printTokens(book, tokenFileString);
			} catch (Exception e) {
				continue;
			}
		}
	}
}
