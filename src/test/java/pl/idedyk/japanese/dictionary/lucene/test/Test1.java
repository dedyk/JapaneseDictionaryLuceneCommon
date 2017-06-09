package pl.idedyk.japanese.dictionary.lucene.test;

import java.util.List;

import pl.idedyk.japanese.dictionary.api.dictionary.DictionaryManagerAbstract;
import pl.idedyk.japanese.dictionary.api.dictionary.IDatabaseConnector;
import pl.idedyk.japanese.dictionary.api.dictionary.dto.FindKanjiRequest;
import pl.idedyk.japanese.dictionary.api.dictionary.dto.FindKanjiResult;
import pl.idedyk.japanese.dictionary.api.dictionary.dto.WordPlaceSearch;
import pl.idedyk.japanese.dictionary.api.dto.KanjiEntry;
import pl.idedyk.japanese.dictionary.api.dto.RadicalInfo;
import pl.idedyk.japanese.dictionary.api.dto.TransitiveIntransitivePair;
import pl.idedyk.japanese.dictionary.api.keigo.KeigoHelper;
import pl.idedyk.japanese.dictionary.api.tools.KanaHelper;
import pl.idedyk.japanese.dictionary.lucene.LuceneDatabase;

public class Test1 {

	public static void main(String[] args) throws Exception {
		
		/*
		// test tokenizera
		LuceneAnalyzer analyzerTest = new LuceneAnalyzer(Version.LUCENE_47, true);
		
		//String text = "1";
		//String text = "1 ala";
		//String text = "Ala ma kota i psa";
		//String text = "Ala ma kota i psa2 i abc";
		//String text = "dziesięć lat";
		//String text = "お早う御座います";
		//String text = "Mały, skromny japoński pomocnik";
		String text = "sprawiedliwoŚĆ";
		
		TokenStream tokenStream = analyzerTest.tokenStream("a", text);
		
		tokenStream.reset();
		
		while(true) {
			
			boolean incrementTokenResult = tokenStream.incrementToken();
			
			if (incrementTokenResult == false) {
				break;
			}
			
			System.out.println("Token: " + tokenStream.getAttribute(CharTermAttribute.class).toString());			
		}
						
		System.exit(1);
		*/
		
		//
		
		LuceneDatabase luceneDatabase = new LuceneDatabase("/tmp/a/db-lucene");
		
		luceneDatabase.open();
		
		//
		
		DictionaryManagerTest dictionaryManager = new DictionaryManagerTest(luceneDatabase);
		
		//		

		/*
		FindWordRequest findWordRequest = new FindWordRequest();
		
		//findWordRequest.searchGrammaFormAndExamples = false;		
		//findWordRequest.word = "shukujitsu";
		
		findWordRequest.word = "kot";

		DictionaryEntryType[] dictionaryEntryTypeValues = DictionaryEntryType.values();
		
		List<DictionaryEntryType> dictionaryEntryTypeList = new ArrayList<DictionaryEntryType>();
		
		for (DictionaryEntryType dictionaryEntryType : dictionaryEntryTypeValues) {
			dictionaryEntryTypeList.add(dictionaryEntryType);
		}
		
		//dictionaryEntryTypeList.remove(DictionaryEntryType.WORD_NOUN);
		//..dictionaryEntryTypeList.remove(DictionaryEntryType.WORD_ADJECTIVE_NO);
		
		//findWordRequest.dictionaryEntryTypeList = dictionaryEntryTypeList;
		
		findWordRequest.wordPlaceSearch = WordPlaceSearch.ANY_PLACE;
		
		FindWordResult findWordResult = dictionaryManager.findWord(findWordRequest);
		
		for (ResultItem resultItem : findWordResult.result) {
			
			DictionaryEntry dictionaryEntry = resultItem.getDictionaryEntry();
			
			System.out.println(dictionaryEntry.getId() + " - " + dictionaryEntry.getDictionaryEntryTypeList() + " - " + dictionaryEntry.getKanji() + " - " + 
					dictionaryEntry.getKana() + " - " + dictionaryEntry.getRomaji() + " - " + dictionaryEntry.getTranslates() + " - " +
					dictionaryEntry.getInfo());
			
			System.out.println("--------");
		}
		*/
		
		FindKanjiRequest findKanjiRequest = new FindKanjiRequest();
		
		findKanjiRequest.word = "kot";
		findKanjiRequest.wordPlaceSearch = WordPlaceSearch.ANY_PLACE;
		// findKanjiRequest.strokeCountFrom =
		// findKanjiRequest.strokeCountTo =
		// findKanjiRequest.searchOnlyTop2500 = 
		
		FindKanjiResult findKanjiResult = dictionaryManager.findKanji(findKanjiRequest);
		
		for (KanjiEntry kanjiEntry : findKanjiResult.result) {			
			System.out.println(kanjiEntry.getKanji() + " - " + kanjiEntry.getPolishTranslates() + "- " + kanjiEntry.getInfo());			
		}
				
		//
		
		luceneDatabase.close();
	}
	
	private static class DictionaryManagerTest extends DictionaryManagerAbstract {

		public DictionaryManagerTest(IDatabaseConnector databaseConnector) {
			this.databaseConnector = databaseConnector;
		}
		
		@Override
		public KanaHelper getKanaHelper() {
			return new KanaHelper();
		}

		@Override
		public KeigoHelper getKeigoHelper() {
			throw new UnsupportedOperationException();
		}

		@Override
		public List<TransitiveIntransitivePair> getTransitiveIntransitivePairsList() {
			throw new UnsupportedOperationException();
		}

		@Override
		public void waitForDatabaseReady() {
			return;
		}

		@Override
		public List<RadicalInfo> getRadicalList() {
			throw new UnsupportedOperationException();
		}
	}
}
