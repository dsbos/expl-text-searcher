package search;

import com.sun.corba.se.impl.orbutil.ObjectStreamClassUtil_1_3;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.LocatorEx;
import sun.security.provider.certpath.OCSPResponse;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// ~Problems:
// - style: uses tabs instead of spaces
// - depends on default character set
// - doesn't close input file
// - style: no spaces after commas



public class TextSearcher {

    private final String inputText;

    private final List<Occurrence> occurrences = new ArrayList<>();

    private Map<String, List<Occurrence>> occurrenceListsByWord =
            new HashMap<>();

    private static boolean isWordChar(char ch) {
        return (    '0' <= ch && ch <= '9')
                || ('A' <= ch && ch <= 'Z')
                || ('a' <= ch && ch <= 'z')
                || '\'' == ch;
    }

    private static String canonicalizeWord(String word) {
        return word.toLowerCase();
    }

    /**
      * Represents an occurrence of a word in the indexed input test.
      * @param  occOffset      ordinal position among all word occurrence (for
      *                          indexing into sibling occurrences to get
      *                          context)
      * @param  startChOffset  occurrence's starting character offset in input
      * @param  endChOffset    occurrence's end character offset in inpur
      */
	private static class Occurrence {
		int occOffset;
		int startChOffset;
		int endChOffset;
	}


    //??????????

    //???? add list parameter, to make static?
    private void addOccurrence(int startChOffset, int endCharOffset) {
	    System.err.println("addOccurrence(" + startChOffset + ", " + endCharOffset + ")");
        int occOffset = occurrences.size();

        final Occurrence temp = new Occurrence();
        temp.occOffset = occOffset;
        temp.startChOffset = startChOffset;
        temp.endChOffset = endCharOffset;

        occurrences.add(temp);
    }


    private static enum CharClass { WordChar, OtherChar }
    /** Lexing (tokenizing) state. */
    private static enum LexState { InWord, InOther }

	private void/*List<Occurrence>*/ findWordOccurrences(String inputText) {
	    int lastWordStartOffset = -1;

        LexState lexState = LexState.InOther;
	    for (int cx = 0; cx < inputText.length(); cx++) {
            char ch = inputText.charAt(cx);
            System.err.println("@ " + cx + ": '" + ch + "'");
            CharClass charClass =
					isWordChar(ch) ? CharClass.WordChar : CharClass.OtherChar;
            System.err.println("before char.: lexState = " + lexState);
            switch (lexState) {
				case InOther:
					switch (charClass) {
                        case OtherChar:  // another non-word char
                            // None
                            break;
                        case WordChar:   // first character of word--word start
                            lexState = LexState.InWord;
                            lastWordStartOffset = cx;
                            // None
                            break;
                    }
                    break;
                case InWord:
                    switch (charClass) {
                        case WordChar:  // another word char
                            //  None
                            break;
                        case OtherChar: // non-word char after word--word end
                            lexState = LexState.InOther;
     	                    addOccurrence(lastWordStartOffset, cx);
     	                    break;
                    }
            }
        }
        System.err.println("after char.: lexState = " + lexState);
        switch (lexState) {
            case InOther:
                break;
            case InWord:  // not-yet-terminated word--word end
                lexState = LexState.InOther;  // (not really needed)
              addOccurrence(lastWordStartOffset, -1 + inputText.length());
              break;
        }
    }

    private void indexOccurrences(String inputText) {
	    //?? Maybe optimize:  index each occurrence as we find occurrences, when
        //   characters we just scanned, and Occurrences we created, are still
        //   in CPUcache


        occurrences.forEach(occurrence -> {
            String occurrenceString =
                    inputText.substring(occurrence.startChOffset,
                                        occurrence.endChOffset);
            System.err.println("indexOccurrences(...): occurrenceString = '" + occurrenceString + "'");
            String canonicalString = canonicalizeWord(occurrenceString);
            List<Occurrence> currentList =
                    occurrenceListsByWord.computeIfAbsent(canonicalString,
                                                          key -> new ArrayList<>());
            currentList.add(occurrence);
        });
    }


    /**
   	 *  Initializes any internal data structures that are needed for
   	 *  this class to implement search efficiently.
   	 */
   	protected void init(String fileContents) {
        findWordOccurrences(fileContents);
        indexOccurrences(fileContents);
   	}

	/**
	 * Initializes the text searcher with the contents of a text file.
	 * The current implementation just reads the contents into a string 
	 * and passes them to #init().  You may modify this implementation if you need to.
	 * 
	 * @param f Input file.
	 * @throws IOException
	 */
	public TextSearcher(File f) throws IOException {
		FileReader r = new FileReader(f);
		StringWriter w = new StringWriter();
		char[] buf = new char[4096];
		int readCount;
		
		while ((readCount = r.read(buf)) > 0) {
			w.write(buf,0,readCount);
		}

		inputText = w.toString();
		init(inputText);
	}
	


	/*

	      (inputText + ' ')   //????? try to fix end hack (avoid copying input)

	 */

	/**
	 * 
	 * @param queryWord The word to search for in the file contents.
	 * @param contextWords The number of words of context to provide on
	 *                     each side of the query word.
	 * @return One context string for each time the query word appears in the file.
	 */
	public String[] search(String queryWord, int contextWords) {
        String canonicalQueryWord = canonicalizeWord(queryWord);
        List<Occurrence> wordOccurrences =
                occurrenceListsByWord.getOrDefault(canonicalQueryWord,
                                                   Collections.emptyList());

        List<String> nameThis1 =
            wordOccurrences.stream().map(occurrence -> {
                int occOffset = occurrence.occOffset;
                System.err.println("occurrence #i: " + occurrence);

                final int contextStartCharOffset;
                {
                    int nominalContextStartOccurrenceOffset = occOffset - contextWords;
                    if (nominalContextStartOccurrenceOffset < 0) {
                        contextStartCharOffset = 0;
                    }
                    else {
                        Occurrence startOccurrence = occurrences.get(nominalContextStartOccurrenceOffset);
                        contextStartCharOffset = startOccurrence.startChOffset;
                    }
                }
                final int contextEndCharOffset;
                {
                    int nominalContextEndOccurrenceOffset = occOffset + contextWords;
                    if (nominalContextEndOccurrenceOffset > occurrences.size() - 1) {
                        contextEndCharOffset = inputText.length();
                    }
                    else {
                        Occurrence endOccurrence = occurrences.get(nominalContextEndOccurrenceOffset);
                        contextEndCharOffset = endOccurrence.endChOffset;
                    }
                }

                String resultString = inputText.substring(contextStartCharOffset, contextEndCharOffset);
                return resultString;

            }).collect(Collectors.toList());

        String[] nameThis2 = nameThis1.toArray(new String[0]);

/*


   	      val results = {
   	        wordOccurrences.map(wordOccurrence => {
   	          resultString
   	        })
   	      }
   	      results
   	    }

*/
        return nameThis2;
	}
}

// Any needed utility classes can just go in this file

