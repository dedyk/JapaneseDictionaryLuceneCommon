package pl.idedyk.japanese.dictionary.lucene;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.analysis.core.SimpleAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import pl.idedyk.japanese.dictionary.api.dictionary.Utils;
import pl.idedyk.japanese.dictionary.api.dictionary.lucene.LuceneStatic;
import pl.idedyk.japanese.dictionary.api.dto.DictionaryEntry;
import pl.idedyk.japanese.dictionary.api.dto.DictionaryEntryType;
import pl.idedyk.japanese.dictionary.api.dto.GroupEnum;
import pl.idedyk.japanese.dictionary.api.dto.GroupWithTatoebaSentenceList;
import pl.idedyk.japanese.dictionary.api.dto.KanjiDic2Entry;
import pl.idedyk.japanese.dictionary.api.dto.KanjiEntry;
import pl.idedyk.japanese.dictionary.api.dto.KanjivgEntry;
import pl.idedyk.japanese.dictionary.api.dto.RadicalInfo;
import pl.idedyk.japanese.dictionary.api.dto.TatoebaSentence;
import pl.idedyk.japanese.dictionary.api.example.ExampleManager;
import pl.idedyk.japanese.dictionary.api.exception.DictionaryException;
import pl.idedyk.japanese.dictionary.api.gramma.GrammaConjugaterManager;
import pl.idedyk.japanese.dictionary.api.gramma.dto.GrammaFormConjugateResult;
import pl.idedyk.japanese.dictionary.api.gramma.dto.GrammaFormConjugateResultType;
import pl.idedyk.japanese.dictionary.api.keigo.KeigoHelper;

import com.csvreader.CsvReader;

public class LuceneDBGenerator {	

	
	public static void main(String[] args) throws Exception {
	
		// parametry
		String dictionaryFilePath = args[0];
		String sentencesFilePath = args[1];
		String sentencesGroupsFilePath = args[2];		
		String kanjiFilePath = args[3];
		String radicalFilePath = args[4];
		
		boolean addSugestionList = Boolean.parseBoolean(args[5]);
		//boolean addGrammaAndExample = Boolean.parseBoolean(args[6]);
		
		String dbOutDir = args[6];
				
		final File dbOutDirFile = new File(dbOutDir);
		
		if (dbOutDirFile.exists() == false) {
			dbOutDirFile.mkdir();
		}
		
		if (dbOutDirFile.isDirectory() == true) {
			
			File[] dbOutDirFileListFiles = dbOutDirFile.listFiles();
			
			for (File file : dbOutDirFileListFiles) {
				file.delete();
			}
		}		
		
		// tworzenie indeksu lucene
		Directory index = FSDirectory.open(dbOutDirFile);
		
		// tworzenie analizatora lucene
		SimpleAnalyzer analyzer = new SimpleAnalyzer(Version.LUCENE_47);
		
		// tworzenie zapisywacza konfiguracji
		IndexWriterConfig indexWriterConfig = new IndexWriterConfig(Version.LUCENE_47, analyzer);
		indexWriterConfig.setOpenMode(OpenMode.CREATE);
		
		IndexWriter indexWriter = new IndexWriter(index, indexWriterConfig);
		
		// otwarcie pliku ze slownikiem
		FileInputStream dictionaryInputStream = new FileInputStream(dictionaryFilePath);

		// wczytywanie slownika
		readDictionaryFile(indexWriter, dictionaryInputStream, addSugestionList);

		dictionaryInputStream.close();
		
		// otwarcie pliku ze zdaniami
		FileInputStream sentencesInputStream = new FileInputStream(sentencesFilePath);
		
		// wczytanie zdan
		readSentenceFile(indexWriter, sentencesInputStream);
		
		sentencesInputStream.close();
		
		// otwarcie pliku z przydzialem zdan do grup
		FileInputStream sentencesGroupsInputStream = new FileInputStream(sentencesGroupsFilePath);
		
		// wczytanie przydzialu zdan do grup
		readSentenceGroupsFile(indexWriter, sentencesGroupsInputStream);
		
		sentencesGroupsInputStream.close();
		
		// otwarcie pliku ze znakami podstawowymi
		FileInputStream radicalInputStream = new FileInputStream(radicalFilePath);

		// wczytywanie pliku ze znakami podstawowymi
		List<RadicalInfo> radicalInfoList = readRadicalEntriesFromCsv(radicalInputStream);

		radicalInputStream.close();

		// otwarcie pliku ze znakami kanji
		FileInputStream kanjiInputStream = new FileInputStream(kanjiFilePath);

		// wczytywanie pliku ze znakami kanji
		readKanjiDictionaryFile(indexWriter, radicalInfoList, kanjiInputStream, addSugestionList);

		kanjiInputStream.close();
		
		// zakonczenie zapisywania indeksu
		indexWriter.close();
				
		System.out.println("DB Generator - done");
	}

	private static void readDictionaryFile(IndexWriter indexWriter, InputStream dictionaryInputStream, boolean addSugestionList
			/* boolean addGrammaAndExample */) throws IOException, DictionaryException, SQLException {

		KeigoHelper keigoHelper = new KeigoHelper();

		CsvReader csvReader = new CsvReader(new InputStreamReader(dictionaryInputStream), ',');

		Set<GroupEnum> uniqueDictionaryEntryGroupEnumSet = new HashSet<GroupEnum>();

		while (csvReader.readRecord()) {

			String idString = csvReader.get(0);
			String dictionaryEntryTypeString = csvReader.get(1);
			String attributesString = csvReader.get(2);
			String groupsString = csvReader.get(4);
			String prefixKanaString = csvReader.get(5);
			String kanjiString = csvReader.get(6);

			String kanaListString = csvReader.get(7);
			String prefixRomajiString = csvReader.get(8);

			String romajiListString = csvReader.get(9);
			String translateListString = csvReader.get(10);
			String infoString = csvReader.get(11);
			
			String exampleSentenceGroupIdsListString = csvReader.get(13);
			
			DictionaryEntry entry = Utils.parseDictionaryEntry(idString, dictionaryEntryTypeString, attributesString,
					groupsString, prefixKanaString, kanjiString, kanaListString, prefixRomajiString, romajiListString,
					translateListString, infoString, exampleSentenceGroupIdsListString);

			// count form for dictionary entry
			Map<GrammaFormConjugateResultType, GrammaFormConjugateResult> grammaFormCache = new HashMap<GrammaFormConjugateResultType, GrammaFormConjugateResult>();

			//List<GrammaFormConjugateGroupTypeElements> grammaConjufateResult = 
			GrammaConjugaterManager.getGrammaConjufateResult(keigoHelper, entry, grammaFormCache, null);
			
			/*
			if (addGrammaAndExample == true) {
				addGrammaConjufateResult(indexWriter, entry, grammaConjufateResult);
			}
			*/
			
			for (DictionaryEntryType currentDictionaryEntryType : entry.getDictionaryEntryTypeList()) {
				GrammaConjugaterManager.getGrammaConjufateResult(keigoHelper, entry, grammaFormCache,
						currentDictionaryEntryType);
			}

			ExampleManager.getExamples(keigoHelper, entry, grammaFormCache, null);

			for (DictionaryEntryType currentDictionaryEntryType : entry.getDictionaryEntryTypeList()) {
				ExampleManager.getExamples(keigoHelper, entry, grammaFormCache, currentDictionaryEntryType);
			}

			addDictionaryEntry(indexWriter, entry, addSugestionList);

			uniqueDictionaryEntryGroupEnumSet.addAll(entry.getGroups());
		}

		addDictionaryEntryUniqueGroupEnum(indexWriter, uniqueDictionaryEntryGroupEnumSet);

		csvReader.close();
	}

	private static void addDictionaryEntry(IndexWriter indexWriter, DictionaryEntry dictionaryEntry, boolean addSugestionList) throws IOException {
		
		Document document = new Document();
		
		// object type
		document.add(new StringField(LuceneStatic.objectType, LuceneStatic.dictionaryEntry_objectType, Field.Store.YES));
		
		// id
		document.add(new IntField(LuceneStatic.dictionaryEntry_id, dictionaryEntry.getId(), Field.Store.YES));
		
		// dictionary entry type list
		List<String> dictionaryEntryTypeStringList = DictionaryEntryType.convertToValues(dictionaryEntry.getDictionaryEntryTypeList());
		
		for (String dictionaryEntryTypeString : dictionaryEntryTypeStringList) {
			document.add(new StringField(LuceneStatic.dictionaryEntry_dictionaryEntryTypeList, dictionaryEntryTypeString, Field.Store.YES));
		}
				
		// attributeList
		List<String> attributeStringList = dictionaryEntry.getAttributeList().convertAttributeListToListString();
		
		for (String currentAttribute : attributeStringList) {
			document.add(new StringField(LuceneStatic.dictionaryEntry_attributeList, currentAttribute, Field.Store.YES));
		}
		
		// groupsList
		List<String> groupsList = GroupEnum.convertToValues(dictionaryEntry.getGroups());
		
		for (String currentGroup : groupsList) {
			document.add(new StringField(LuceneStatic.dictionaryEntry_groupsList, currentGroup, Field.Store.YES));
		}
		
		// prefixKana
		document.add(new StringField(LuceneStatic.dictionaryEntry_prefixKana, emptyIfNull(dictionaryEntry.getPrefixKana()), Field.Store.YES));
		
		// kanji
		document.add(new StringField(LuceneStatic.dictionaryEntry_kanji, emptyIfNull(dictionaryEntry.getKanji()), Field.Store.YES));
		
		if (addSugestionList == true) {
			document.add(new StringField(LuceneStatic.dictionaryEntry_sugestionList, emptyIfNull(dictionaryEntry.getKanji()), Field.Store.YES));
		}
		
		// kanaList
		List<String> kanaList = dictionaryEntry.getKanaList();
		
		for (String currentKana : kanaList) {
			document.add(new StringField(LuceneStatic.dictionaryEntry_kanaList, currentKana, Field.Store.YES));
			
			if (addSugestionList == true) {
				document.add(new StringField(LuceneStatic.dictionaryEntry_sugestionList, currentKana, Field.Store.YES));
			}
		}
		
		// prefixRomaji
		document.add(new StringField(LuceneStatic.dictionaryEntry_prefixRomaji, emptyIfNull(dictionaryEntry.getPrefixRomaji()), Field.Store.YES));
		
		// romajiList
		List<String> romajiList = dictionaryEntry.getRomajiList();
		
		for (String currentRomaji : romajiList) {
			document.add(new TextField(LuceneStatic.dictionaryEntry_romajiList, currentRomaji, Field.Store.YES));
			
			if (addSugestionList == true) {
				document.add(new StringField(LuceneStatic.dictionaryEntry_sugestionList, currentRomaji, Field.Store.YES));
			}
		}
		
		// translatesList
		List<String> translates = dictionaryEntry.getTranslates();
		
		for (String currentTranslate : translates) {
			
			document.add(new TextField(LuceneStatic.dictionaryEntry_translatesList, currentTranslate, Field.Store.YES));
			
			if (addSugestionList == true) {
				document.add(new StringField(LuceneStatic.dictionaryEntry_sugestionList, currentTranslate, Field.Store.YES));
			}
			
			String currentTranslateWithoutPolishChars = Utils.removePolishChars(currentTranslate);
				
			document.add(new TextField(LuceneStatic.dictionaryEntry_translatesListWithoutPolishChars, currentTranslateWithoutPolishChars, Field.Store.YES));
		}
		
		// info
		String info = emptyIfNull(dictionaryEntry.getInfo());
		
		document.add(new TextField(LuceneStatic.dictionaryEntry_info, info, Field.Store.YES));
			
		String infoWithoutPolishChars = Utils.removePolishChars(info);
			
		document.add(new TextField(LuceneStatic.dictionaryEntry_infoWithoutPolishChars, infoWithoutPolishChars, Field.Store.YES));
		
		// example sentence groupIds
		List<String> exampleSentenceGroupIdsList = dictionaryEntry.getExampleSentenceGroupIdsList();
				
		for (String currentExampleSenteceGroupId : exampleSentenceGroupIdsList) {
			document.add(new TextField(LuceneStatic.dictionaryEntry_exampleSentenceGroupIdsList, currentExampleSenteceGroupId, Field.Store.YES));
		}
		
		indexWriter.addDocument(document);
	}
	
	/*
	private static void addGrammaConjufateResult(IndexWriter indexWriter, DictionaryEntry dictionaryEntry, List<GrammaFormConjugateGroupTypeElements> grammaConjufateResult) throws IOException {
		
		if (grammaConjufateResult == null) {
			return;
		}
		
		for (GrammaFormConjugateGroupTypeElements grammaFormConjugateGroupTypeElements : grammaConjufateResult) {
						
			List<GrammaFormConjugateResult> grammaFormConjugateResults = grammaFormConjugateGroupTypeElements.getGrammaFormConjugateResults();
			
			for (GrammaFormConjugateResult grammaFormConjugateResult : grammaFormConjugateResults) {
				
				Document document = new Document();
				
				// object type
				document.add(new StringField(LuceneStatic.objectType, LuceneStatic.dictionaryEntry_grammaConjufateResult_objectType, Field.Store.YES));
				
				// id
				document.add(new IntField(LuceneStatic.dictionaryEntry_grammaConjufateResult_dictionaryEntry_id, dictionaryEntry.getId(), Field.Store.YES));

				// kanji
				if (grammaFormConjugateResult.isKanjiExists() == true) {					
					document.add(new StringField(grammaFormConjugateResult.getKanji(), LuceneStatic.dictionaryEntry_grammaConjufateResult_kanji, Field.Store.YES));
				}
				
				// kanaList
				List<String> kanaList = grammaFormConjugateResult.getKanaList();
				
				for (String currentKana : kanaList) {
					document.add(new StringField(currentKana, LuceneStatic.dictionaryEntry_grammaConjufateResult_kanaList, Field.Store.YES));
				}
				
				// romajiList 
				List<String> romajiList = grammaFormConjugateResult.getRomajiList();
				
				for (String currentRomaji : romajiList) {
					document.add(new StringField(currentRomaji, LuceneStatic.dictionaryEntry_grammaConjufateResult_romajiList, Field.Store.YES));
				}

				indexWriter.addDocument(document);				
			}
		}		
	}
	*/
	
	private static void readSentenceFile(IndexWriter indexWriter, FileInputStream sentencesInputStream) throws IOException {
		
		CsvReader csvReader = new CsvReader(new InputStreamReader(sentencesInputStream), ',');
		
		while (csvReader.readRecord()) {
			
			String id = csvReader.get(0);
			String lang = csvReader.get(1);
			String sentence = csvReader.get(2);
			
			TatoebaSentence tatoebaSentence = new TatoebaSentence();
			
			tatoebaSentence.setId(id);
			tatoebaSentence.setLang(lang);
			tatoebaSentence.setSentence(sentence);
			
			addTatoebaSentence(indexWriter, tatoebaSentence);
		}		
		
		csvReader.close();
	}

	private static void addTatoebaSentence(IndexWriter indexWriter, TatoebaSentence tatoebaSentence) throws IOException {
		
		Document document = new Document();
		
		// object type
		document.add(new StringField(LuceneStatic.objectType, LuceneStatic.dictionaryEntry_exampleSentence_objectType, Field.Store.YES));
		
		// id
		document.add(new StringField(LuceneStatic.dictionaryEntry_exampleSentence_id, tatoebaSentence.getId(), Field.Store.YES));

		// lang
		document.add(new StringField(LuceneStatic.dictionaryEntry_exampleSentence_lang, tatoebaSentence.getLang(), Field.Store.YES));
		
		// sentence
		document.add(new StringField(LuceneStatic.dictionaryEntry_exampleSentence_sentence, tatoebaSentence.getSentence(), Field.Store.YES));
		
		indexWriter.addDocument(document);
	}

	private static void addDictionaryEntryUniqueGroupEnum(IndexWriter indexWriter, Set<GroupEnum> uniqueDictionaryEntryGroupEnumSet) throws IOException {
		
		List<GroupEnum> uniqueDictionaryEntryGroupEnumList = GroupEnum.sortGroups(new ArrayList<GroupEnum>(uniqueDictionaryEntryGroupEnumSet));
				
		Document document = new Document();
		
		// object type
		document.add(new StringField(LuceneStatic.objectType, LuceneStatic.uniqueDictionaryEntryGroupEnumList_objectType, Field.Store.YES));

		for (GroupEnum groupEnum : uniqueDictionaryEntryGroupEnumList) {
			document.add(new StringField(LuceneStatic.uniqueDictionaryEntryGroupEnumList_groupsList, groupEnum.getValue(), Field.Store.YES));
		}		
		
		indexWriter.addDocument(document);
	}	
	
	private static void readSentenceGroupsFile(IndexWriter indexWriter, FileInputStream sentencesGroupsInputStream) throws IOException {
		
		CsvReader csvReader = new CsvReader(new InputStreamReader(sentencesGroupsInputStream), ',');
		
		while (csvReader.readRecord()) {
			
			String groupId = csvReader.get(0);
			String sentenceIdListString = csvReader.get(1);
			
			List<String> sentenceIdList = Utils.parseStringIntoList(sentenceIdListString, false);
			
			GroupWithTatoebaSentenceList groupWithTatoebaSentenceList = new GroupWithTatoebaSentenceList();

			List<TatoebaSentence> tatoebaSentenceLazyList = new ArrayList<TatoebaSentence>();
			
			for (String currentSentenceId : sentenceIdList) {
				TatoebaSentence tatoebaSentence = new TatoebaSentence();
				
				tatoebaSentence.setId(currentSentenceId);
				
				tatoebaSentenceLazyList.add(tatoebaSentence);
			}
			
			groupWithTatoebaSentenceList.setGroupId(groupId);
			groupWithTatoebaSentenceList.setTatoebaSentenceList(tatoebaSentenceLazyList);
			
			addGroupWithTatoebaSentenceList(indexWriter, groupWithTatoebaSentenceList);
		}		
		
		csvReader.close();	
	}
	
	private static void addGroupWithTatoebaSentenceList(IndexWriter indexWriter, GroupWithTatoebaSentenceList groupWithTatoebaSentenceList) throws IOException {
		
		Document document = new Document();
		
		// object type
		document.add(new StringField(LuceneStatic.objectType, LuceneStatic.dictionaryEntry_exampleSentenceGroups_objectType, Field.Store.YES));
		
		// id
		document.add(new StringField(LuceneStatic.dictionaryEntry_exampleSentenceGroups_groupId, groupWithTatoebaSentenceList.getGroupId(), Field.Store.YES));

		// lang
		List<TatoebaSentence> tatoebaSentenceList = groupWithTatoebaSentenceList.getTatoebaSentenceList();
		
		for (TatoebaSentence currentTatoebaSentence : tatoebaSentenceList) {
			document.add(new StringField(LuceneStatic.dictionaryEntry_exampleSentenceGroups_sentenceIdList, currentTatoebaSentence.getId(), Field.Store.YES));
		}
				
		indexWriter.addDocument(document);
	}
	
	private static List<RadicalInfo> readRadicalEntriesFromCsv(InputStream radicalInputStream) throws IOException,
			DictionaryException {

		List<RadicalInfo> radicalList = new ArrayList<RadicalInfo>();

		CsvReader csvReader = new CsvReader(new InputStreamReader(radicalInputStream), ',');

		while (csvReader.readRecord()) {

			int id = Integer.parseInt(csvReader.get(0));

			String radical = csvReader.get(1);

			if (radical.equals("") == true) {
				throw new DictionaryException("Empty radical: " + radical);
			}

			String strokeCountString = csvReader.get(2);

			int strokeCount = Integer.parseInt(strokeCountString);

			RadicalInfo entry = new RadicalInfo();

			entry.setId(id);
			entry.setRadical(radical);
			entry.setStrokeCount(strokeCount);

			radicalList.add(entry);
		}

		csvReader.close();

		return radicalList;
	}

	private static void readKanjiDictionaryFile(IndexWriter indexWriter, List<RadicalInfo> radicalInfoList,
			InputStream kanjiInputStream, boolean addSugestionList) throws IOException, DictionaryException, SQLException {

		Map<String, RadicalInfo> radicalListMapCache = new HashMap<String, RadicalInfo>();

		for (RadicalInfo currentRadicalInfo : radicalInfoList) {

			String radical = currentRadicalInfo.getRadical();

			radicalListMapCache.put(radical, currentRadicalInfo);
		}
		
		Set<String> allAvailableRadicalSet = new HashSet<String>();

		CsvReader csvReader = new CsvReader(new InputStreamReader(kanjiInputStream), ',');

		while (csvReader.readRecord()) {

			String idString = csvReader.get(0);

			String kanjiString = csvReader.get(1);

			String strokeCountString = csvReader.get(2);

			String radicalsString = csvReader.get(3);

			String onReadingString = csvReader.get(4);

			String kunReadingString = csvReader.get(5);

			String strokePathString = csvReader.get(6);

			String polishTranslateListString = csvReader.get(7);
			String infoString = csvReader.get(8);

			String generatedString = csvReader.get(9);

			String groupString = csvReader.get(10);

			KanjiEntry entry = Utils.parseKanjiEntry(idString, kanjiString, strokeCountString,
					Utils.parseStringIntoList(radicalsString, false),
					Utils.parseStringIntoList(onReadingString, false),
					Utils.parseStringIntoList(kunReadingString, false), strokePathString,
					Utils.parseStringIntoList(polishTranslateListString, false), infoString, generatedString,
					Utils.parseStringIntoList(groupString, false));

			// update radical info
			if (entry.getKanjiDic2Entry() != null) {
				//updateRadicalInfoUse(radicalListMapCache, entry.getKanjiDic2Entry().getRadicals());
				
				allAvailableRadicalSet.addAll(entry.getKanjiDic2Entry().getRadicals());
			}

			// add
			addKanjiEntry(indexWriter, entry, addSugestionList);
		}
		
		// add available radical list
		addAvailableRadicalList(indexWriter, allAvailableRadicalSet);

		csvReader.close();
	}
	
	/*
	private static void updateRadicalInfoUse(Map<String, RadicalInfo> radicalListMapCache, List<String> radicals) {

		for (String currentRadical : radicals) {

			RadicalInfo currentRadicalInfo = radicalListMapCache.get(currentRadical);

			if (currentRadicalInfo == null) {
				throw new RuntimeException("currentRadicalInfo == null: " + currentRadical);
			}

			//currentRadicalInfo.incrementUse();			
		}
	}
	*/

	public static void addKanjiEntry(IndexWriter indexWriter, KanjiEntry kanjiEntry, boolean addSugestionList) throws IOException {

		Document document = new Document();
		
		// object type
		document.add(new StringField(LuceneStatic.objectType, LuceneStatic.kanjiEntry_objectType, Field.Store.YES));
		
		// id
		document.add(new IntField(LuceneStatic.kanjiEntry_id, kanjiEntry.getId(), Field.Store.YES));

		// kanji
		document.add(new StringField(LuceneStatic.kanjiEntry_kanji, emptyIfNull(kanjiEntry.getKanji()), Field.Store.YES));
		
		if (addSugestionList == true) {
			document.add(new StringField(LuceneStatic.kanjiEntry_sugestionList, emptyIfNull(kanjiEntry.getKanji()), Field.Store.YES));
		}
			
		// polishTranslatesList
		List<String> polishtranslatesList = kanjiEntry.getPolishTranslates();
		
		for (String currentTranslate : polishtranslatesList) {
			
			document.add(new TextField(LuceneStatic.kanjiEntry_polishTranslatesList, currentTranslate, Field.Store.YES));
			
			if (addSugestionList == true) {
				document.add(new TextField(LuceneStatic.kanjiEntry_sugestionList, currentTranslate, Field.Store.YES));
			}
			
			String currentTranslateWithoutPolishChars = Utils.removePolishChars(currentTranslate);
				
			document.add(new TextField(LuceneStatic.kanjiEntry_infoWithoutPolishChars, currentTranslateWithoutPolishChars, Field.Store.YES));
		}
		
		// info
		String info = emptyIfNull(kanjiEntry.getInfo());
		
		document.add(new TextField(LuceneStatic.kanjiEntry_info, info, Field.Store.YES));
		
		String infoWithoutPolishChars = Utils.removePolishChars(info);
			
		document.add(new TextField(LuceneStatic.kanjiEntry_infoWithoutPolishChars, infoWithoutPolishChars, Field.Store.YES));

		// generated
		document.add(new StringField(LuceneStatic.kanjiEntry_generated, String.valueOf(kanjiEntry.isGenerated()), Field.Store.YES));
				
		// groupsList
		List<String> groupsList = GroupEnum.convertToValues(kanjiEntry.getGroups());
		
		for (String currentGroup : groupsList) {
			document.add(new StringField(LuceneStatic.kanjiEntry_groupsList, currentGroup, Field.Store.YES));
		}
		
		KanjiDic2Entry kanjiDic2Entry = kanjiEntry.getKanjiDic2Entry();

		if (kanjiDic2Entry != null) {

			// kanjiDic2Entry_strokeCount
			document.add(new IntField(LuceneStatic.kanjiEntry_kanjiDic2Entry_strokeCount, kanjiDic2Entry.getStrokeCount(), Field.Store.YES));
			
			// kanjiDic2Entry_onReadingList
			List<String> onReadingList = kanjiDic2Entry.getOnReading();
			
			for (String currentOnReading : onReadingList) {
				document.add(new StringField(LuceneStatic.kanjiEntry_kanjiDic2Entry_onReadingList, currentOnReading, Field.Store.YES));
			}

			// kanjiDic2Entry_kunReadingList
			List<String> kunReadingList = kanjiDic2Entry.getKunReading();
			
			for (String currentKunReading : kunReadingList) {
				document.add(new StringField(LuceneStatic.kanjiEntry_kanjiDic2Entry_kunReadingList, currentKunReading, Field.Store.YES));
			}
			
			// kanjiDic2Entry_radicalsList
			List<String> radicalsList = kanjiDic2Entry.getRadicals();
			
			for (String currentRadical : radicalsList) {
				document.add(new StringField(LuceneStatic.kanjiEntry_kanjiDic2Entry_radicalsList, currentRadical, Field.Store.YES));
			}
			
			// kanjiDic2Entry_jlpt
			Integer jlpt = kanjiDic2Entry.getJlpt();
			
			if (jlpt != null) {
				document.add(new IntField(LuceneStatic.kanjiEntry_kanjiDic2Entry_jlpt, jlpt, Field.Store.YES));
			}			
			
			// kanjiDic2Entry_freq
			Integer freq = kanjiDic2Entry.getFreq();
			
			if (freq != null) {
				document.add(new IntField(LuceneStatic.kanjiEntry_kanjiDic2Entry_freq, freq, Field.Store.YES));
			}
		}
		
		KanjivgEntry kanjivgEntry = kanjiEntry.getKanjivgEntry();
		
		if (kanjivgEntry != null) {
			
			// kanjivgEntry_strokePaths
			List<String> strokePaths = kanjivgEntry.getStrokePaths();
						
			for (String currentStrokePath : strokePaths) {
				document.add(new StringField(LuceneStatic.kanjiEntry_kanjivgEntry_strokePaths, currentStrokePath, Field.Store.YES));
			}
		}
		

		indexWriter.addDocument(document);		
	}
	
	private static void addAvailableRadicalList(IndexWriter indexWriter, Set<String> allAvailableRadicalSet) throws IOException {
		
		List<String> allAvailableRadicalList = new ArrayList<String>(allAvailableRadicalSet);
				
		Document document = new Document();
		
		// object type
		document.add(new StringField(LuceneStatic.objectType, LuceneStatic.allAvailableKanjiRadicals_objectType, Field.Store.YES));

		for (String radical : allAvailableRadicalList) {
			document.add(new StringField(LuceneStatic.allAvailableKanjiRadicals_radicalsList, radical, Field.Store.YES));
		}		
		
		indexWriter.addDocument(document);
	}
	
	private static String emptyIfNull(String text) {
		if (text == null) {
			return "";
		}

		return text;
	}
}
