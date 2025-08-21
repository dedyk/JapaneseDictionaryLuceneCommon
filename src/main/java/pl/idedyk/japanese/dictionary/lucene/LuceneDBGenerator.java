package pl.idedyk.japanese.dictionary.lucene;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.search.spell.JaroWinklerDistance;
import org.apache.lucene.search.spell.LuceneDictionary;
import org.apache.lucene.search.spell.SpellChecker;
import org.apache.lucene.search.suggest.analyzing.AnalyzingSuggester;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import pl.idedyk.japanese.dictionary.api.dictionary.Utils;
import pl.idedyk.japanese.dictionary.api.dto.Attribute;
import pl.idedyk.japanese.dictionary.api.dto.AttributeType;
import pl.idedyk.japanese.dictionary.api.dto.DictionaryEntry;
import pl.idedyk.japanese.dictionary.api.dto.DictionaryEntryType;
import pl.idedyk.japanese.dictionary.api.dto.GroupEnum;
import pl.idedyk.japanese.dictionary.api.dto.GroupWithTatoebaSentenceList;
import pl.idedyk.japanese.dictionary.api.dto.TatoebaSentence;
import pl.idedyk.japanese.dictionary.api.example.ExampleManager;
import pl.idedyk.japanese.dictionary.api.example.dto.ExampleGroupTypeElements;
import pl.idedyk.japanese.dictionary.api.example.dto.ExampleRequest;
import pl.idedyk.japanese.dictionary.api.example.dto.ExampleResult;
import pl.idedyk.japanese.dictionary.api.exception.DictionaryException;
import pl.idedyk.japanese.dictionary.api.gramma.GrammaConjugaterManager;
import pl.idedyk.japanese.dictionary.api.gramma.dto.GrammaFormConjugateGroupTypeElements;
import pl.idedyk.japanese.dictionary.api.gramma.dto.GrammaFormConjugateRequest;
import pl.idedyk.japanese.dictionary.api.gramma.dto.GrammaFormConjugateResult;
import pl.idedyk.japanese.dictionary.api.gramma.dto.GrammaFormConjugateResultType;
import pl.idedyk.japanese.dictionary.api.keigo.KeigoHelper;
import pl.idedyk.japanese.dictionary2.api.helper.Dictionary2HelperCommon;
import pl.idedyk.japanese.dictionary2.api.helper.Dictionary2HelperCommon.KanjiKanaPair;
import pl.idedyk.japanese.dictionary2.jmdict.xsd.Gloss;
import pl.idedyk.japanese.dictionary2.jmdict.xsd.JMdict;
import pl.idedyk.japanese.dictionary2.jmdict.xsd.JMdict.Entry;
import pl.idedyk.japanese.dictionary2.jmdict.xsd.KanjiInfo;
import pl.idedyk.japanese.dictionary2.jmdict.xsd.MiscInfo;
import pl.idedyk.japanese.dictionary2.jmdict.xsd.OldPolishJapaneseDictionaryInfo;
import pl.idedyk.japanese.dictionary2.jmdict.xsd.OldPolishJapaneseDictionaryInfoAttributeListInfo;
import pl.idedyk.japanese.dictionary2.jmdict.xsd.OldPolishJapaneseDictionaryInfoEntriesInfo;
import pl.idedyk.japanese.dictionary2.jmdict.xsd.ReadingInfo;
import pl.idedyk.japanese.dictionary2.jmdict.xsd.Sense;
import pl.idedyk.japanese.dictionary2.jmdict.xsd.SenseAdditionalInfo;
import pl.idedyk.japanese.dictionary2.kanjidic2.xsd.KanjiCharacterInfo;
import pl.idedyk.japanese.dictionary2.kanjidic2.xsd.Kanjidic2;

import com.csvreader.CsvReader;
import com.google.gson.Gson;

public class LuceneDBGenerator {	
	
	public static void main(String[] args) throws Exception {
		
		// android: android db/word.csv db/sentences.csv db/sentences_groups.csv db/kanji2.xml db/radical.csv db/names.csv db/word2.xml db-lucene
		// web: web db/word.csv db/sentences.csv db/sentences_groups.csv db/kanji2.xml db/radical.csv db/names.csv word2.xml db-lucene
		
		// parametry
		String mode = args[0];
		
		String dictionaryFilePath = args[1];
		String sentencesFilePath = args[2];
		String sentencesGroupsFilePath = args[3];
		String kanjiFilePath = args[4];
		// String radicalFilePath = args[5];
		final String nameFilePath = args[6];
		
		final String word2XmlFilePath = args[7];
		
		String dbOutDir = args[8];
		
		boolean addSugestionList;
		boolean addGrammaAndExample;		
		
		boolean generateNamesDictionary;
		
		boolean generateDictionaryEntryPrefixes;
		boolean generateKanjiEntryPrefixes;
		boolean generateNameEntryPrefixes;
		
		boolean addWord2Xml;
		
		if (mode.equals("web") == true) {
			
			addSugestionList = true;
			addGrammaAndExample = true;		
			
			generateNamesDictionary = true;
			
			generateDictionaryEntryPrefixes = true;
			generateKanjiEntryPrefixes = true;
			generateNameEntryPrefixes = true;
			
			addWord2Xml = true;
			
		} else if (mode.equals("android") == true) {
			
			addSugestionList = false;
			addGrammaAndExample = false;	
			
			generateNamesDictionary = false;
			
			generateDictionaryEntryPrefixes = false;
			generateKanjiEntryPrefixes = true;
			generateNameEntryPrefixes = false;
			
			addWord2Xml = true;

		} else {
			throw new Exception("Unknown mode: " + mode);
		}
				
		////
								
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
		LuceneAnalyzer analyzer = new LuceneAnalyzer(Version.LUCENE_47, true);
		
		// tworzenie zapisywacza konfiguracji
		IndexWriterConfig indexWriterConfig = new IndexWriterConfig(Version.LUCENE_47, analyzer);
		indexWriterConfig.setOpenMode(OpenMode.CREATE);
		
		IndexWriter indexWriter = new IndexWriter(index, indexWriterConfig);

		// otwarcie pliku ze slownikiem - FM_FIXME - do usuniecia
		FileInputStream dictionaryInputStream = new FileInputStream(dictionaryFilePath);

		// wczytywanie slownika - FM_FIXME: do usuniecia
		// List<DictionaryEntry> dictionaryEntryList = readDictionaryFile(indexWriter, dictionaryInputStream, addSugestionList, generateDictionaryEntryPrefixes);

		// przeliczenie form - FM_FIXME: do usuniecia
		// countGrammaFormAndExamples(dictionaryEntryList, indexWriter, addGrammaAndExample, addSugestionList);
		
		dictionaryInputStream.close();
		
		// dodawanie pliku word2.xml
		if (addWord2Xml == true) {			
			addWord2Xml(indexWriter, word2XmlFilePath, addGrammaAndExample, addSugestionList, generateDictionaryEntryPrefixes);
		}
		
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
		// FileInputStream radicalInputStream = new FileInputStream(radicalFilePath);

		// wczytywanie pliku ze znakami podstawowymi
		// List<RadicalInfo> radicalInfoList = readRadicalEntriesFromCsv(radicalInputStream);

		// radicalInputStream.close();

		// otwarcie pliku ze znakami kanji
		FileInputStream kanjiInputStream = new FileInputStream(kanjiFilePath);

		// wczytywanie pliku ze znakami kanji
		readKanjiDictionaryFile(indexWriter, /* radicalInfoList, */ kanjiInputStream, addSugestionList, generateKanjiEntryPrefixes);

		kanjiInputStream.close();
		
		// wczytywanie pliku z nazwami
		if (generateNamesDictionary == true) {
			
			File[] namesList = new File(nameFilePath).getParentFile().listFiles(new FileFilter() {
				
				@Override
				public boolean accept(File pathname) {				
					return pathname.getPath().startsWith(nameFilePath);
				}
			});
			
			Arrays.sort(namesList);
			
			Counter idCounter = new Counter(1);
			
			for (File currentName : namesList) {
				
				FileInputStream namesInputStream = new FileInputStream(currentName);
				
				// wczytywanie slownika nazw
				readNamesFile(indexWriter, namesInputStream, idCounter, addSugestionList, generateNameEntryPrefixes);
		
				// zamkniecie pliku
				namesInputStream.close();
			}		
		}	
						
		// zakonczenie zapisywania indeksu
		indexWriter.close();
		
		// dodatkowo jeszcze, generowanie poprawiacza slow
		generateSpellChecker(dbOutDirFile, analyzer, addSugestionList);
		
		// cache'owanie podpowiadacza slow
		cacheSuggester(dbOutDirFile, analyzer, addSugestionList);
		
		System.out.println("DB Generator - done");
	}

	private static DictionaryEntry parseDictionaryEntry(CsvReader csvReader) throws DictionaryException, IOException {
		
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

		return entry;		
	}
	
	private static List<DictionaryEntry> readDictionaryFile(IndexWriter indexWriter, InputStream dictionaryInputStream, boolean addSugestionList, boolean generatePrefixes) throws IOException, DictionaryException, SQLException {
		
		// FM_FIXME: do usuniecia i zabrania potrzebnych rzeczy, np. sugestie
		
		List<DictionaryEntry> dictionaryEntryList = new ArrayList<DictionaryEntry>();

		CsvReader csvReader = new CsvReader(new InputStreamReader(dictionaryInputStream), ',');

		Set<GroupEnum> uniqueDictionaryEntryGroupEnumSet = new HashSet<GroupEnum>();

		while (csvReader.readRecord()) {

			DictionaryEntry entry = parseDictionaryEntry(csvReader);
			
			System.out.println(String.format("DictionaryEntry id = %s", entry.getId()));

			// addDictionaryEntry(indexWriter, entry, addSugestionList, generatePrefixes);

			uniqueDictionaryEntryGroupEnumSet.addAll(entry.getGroups());
			
			dictionaryEntryList.add(entry);			
		}

		addDictionaryEntryUniqueGroupEnum(indexWriter, uniqueDictionaryEntryGroupEnumSet);

		csvReader.close();
		
		return dictionaryEntryList;
	}

	private static void addDictionaryEntry_TO_DELETE(IndexWriter indexWriter, DictionaryEntry dictionaryEntry, boolean addSugestionList, boolean generatePrefixes) throws IOException {
		
		/*
		// wyliczenie boost'era
		Float boostFloat = getBoostFloat(dictionaryEntry);
		
		//
		
		Document document = new Document();
				
		// object type
		document.add(new StringField(LuceneStatic.objectType, LuceneStatic.dictionaryEntry_objectType, Field.Store.YES));
		
		// id
		document.add(new IntField(LuceneStatic.dictionaryEntry_id, dictionaryEntry.getId(), Field.Store.YES));
		
		// unique key
		String uniqueKey = dictionaryEntry.getUniqueKey();
		
		if (uniqueKey != null) {
			document.add(new StringField(LuceneStatic.dictionaryEntry_uniqueKey, uniqueKey, Field.Store.YES));
		}
		
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
		
		addPrefixes(document, LuceneStatic.dictionaryEntry_kanji, emptyIfNull(dictionaryEntry.getKanji()), generatePrefixes, boostFloat);
		
		if (addSugestionList == true) {
			
			addSuggestion(document, LuceneStatic.dictionaryEntry_android_sugestionList, emptyIfNull(dictionaryEntry.getKanji()), false);
			addSuggestion(document, LuceneStatic.dictionaryEntry_web_sugestionList, emptyIfNull(dictionaryEntry.getKanji()), false);
			
			addSpellChecker(document, LuceneStatic.dictionaryEntry_android_spellCheckerList, emptyIfNull(dictionaryEntry.getKanji()));
			addSpellChecker(document, LuceneStatic.dictionaryEntry_web_spellCheckerList, emptyIfNull(dictionaryEntry.getKanji()));
		}
		
		// kanaList
		String kana = dictionaryEntry.getKana();
		
		document.add(new StringField(LuceneStatic.dictionaryEntry_kana, kana, Field.Store.YES));
		
		addPrefixes(document, LuceneStatic.dictionaryEntry_kana, kana, generatePrefixes, boostFloat);
		
		if (addSugestionList == true) {
			
			addSuggestion(document, LuceneStatic.dictionaryEntry_android_sugestionList, kana, false);
			addSuggestion(document, LuceneStatic.dictionaryEntry_web_sugestionList, kana, false);
			
			addSpellChecker(document, LuceneStatic.dictionaryEntry_android_spellCheckerList, kana);
			addSpellChecker(document, LuceneStatic.dictionaryEntry_web_spellCheckerList, kana);
		}
		
		// prefixRomaji
		document.add(new StringField(LuceneStatic.dictionaryEntry_prefixRomaji, emptyIfNull(dictionaryEntry.getPrefixRomaji()), Field.Store.YES));
		
		// romajiList
		String romaji = dictionaryEntry.getRomaji();
				
		document.add(setBoost(new TextField(LuceneStatic.dictionaryEntry_romaji, romaji, Field.Store.YES), boostFloat));

		addPrefixes(document, LuceneStatic.dictionaryEntry_romaji, romaji, generatePrefixes, boostFloat);
		
		//
		
		// dodanie alternatyw romaji
		addAlternativeRomaji(document, LuceneStatic.dictionaryEntry_virtual_romaji, romaji, generatePrefixes, boostFloat);
		
		if (addSugestionList == true) {
			
			addSuggestion(document, LuceneStatic.dictionaryEntry_android_sugestionList, romaji, false);
			addSuggestion(document, LuceneStatic.dictionaryEntry_web_sugestionList, romaji, false);
			
			addSpellChecker(document, LuceneStatic.dictionaryEntry_android_spellCheckerList, romaji);
			addSpellChecker(document, LuceneStatic.dictionaryEntry_web_spellCheckerList, romaji);			
		}
		
		// translatesList
		List<String> translates = dictionaryEntry.getTranslates();
		
		for (String currentTranslate : translates) {
						
			document.add(setBoost(new TextField(LuceneStatic.dictionaryEntry_translatesList, currentTranslate, Field.Store.YES), boostFloat));
			
			addPrefixes(document, LuceneStatic.dictionaryEntry_translatesList, currentTranslate, generatePrefixes, boostFloat);
			
			if (addSugestionList == true) {
								
				addSuggestion(document, LuceneStatic.dictionaryEntry_android_sugestionList, currentTranslate, true);
				addSuggestion(document, LuceneStatic.dictionaryEntry_web_sugestionList, currentTranslate, true);
				
				addSpellChecker(document, LuceneStatic.dictionaryEntry_android_spellCheckerList, currentTranslate);
				addSpellChecker(document, LuceneStatic.dictionaryEntry_web_spellCheckerList, currentTranslate);
			}
			
			String currentTranslateWithoutPolishChars = Utils.removePolishChars(currentTranslate);
				
			document.add(setBoost(new TextField(LuceneStatic.dictionaryEntry_translatesListWithoutPolishChars, currentTranslateWithoutPolishChars, Field.Store.NO), boostFloat));
			
			addPrefixes(document, LuceneStatic.dictionaryEntry_translatesListWithoutPolishChars, currentTranslateWithoutPolishChars, generatePrefixes, boostFloat);
		}
		
		// info
		String info = emptyIfNull(dictionaryEntry.getInfo());
		
		document.add(setBoost(new TextField(LuceneStatic.dictionaryEntry_info, info, Field.Store.YES), boostFloat));
		
		addPrefixes(document, LuceneStatic.dictionaryEntry_info, info, generatePrefixes, boostFloat);
			
		String infoWithoutPolishChars = Utils.removePolishChars(info);
			
		document.add(setBoost(new TextField(LuceneStatic.dictionaryEntry_infoWithoutPolishChars, infoWithoutPolishChars, Field.Store.NO), boostFloat));
		
		addPrefixes(document, LuceneStatic.dictionaryEntry_infoWithoutPolishChars, infoWithoutPolishChars, generatePrefixes, boostFloat);
		
		// example sentence groupIds
		List<String> exampleSentenceGroupIdsList = dictionaryEntry.getExampleSentenceGroupIdsList();
				
		for (String currentExampleSenteceGroupId : exampleSentenceGroupIdsList) {
			document.add(new TextField(LuceneStatic.dictionaryEntry_exampleSentenceGroupIdsList, currentExampleSenteceGroupId, Field.Store.YES));
		}
		
		indexWriter.addDocument(document);
		*/
	}
	
	private static Float getBoostFloat(Integer boostInteger) {
		Float boostFloat = null;
		
		if (boostInteger == Integer.MAX_VALUE) {
			boostFloat = null;
			
		} else if (boostInteger < 100) {
			float[] ab = calculateAandBFactor(1, 100, 100, 5); // funkcja przechodzaca przez punkty A(1, 100), B(100, 5)
			
			boostFloat = ab[0] * boostInteger.intValue() + ab[1];
			
		} else if (boostInteger < 110) {
			float[] ab = calculateAandBFactor(100, 5, 110, 2); // funkcja przechodzaca przez punkty A(100, 5), B(110, 2)
			
			boostFloat = ab[0] * boostInteger.intValue() + ab[1];
			
		} /* 
		else if (boostInteger < 1000) {
			float[] ab = calculateAandBFactor(110, 2, 1000, 0); // funkcja przechodzaca przez punkty A(110, 2), B(1000, 0)
			
			boostFloat = ab[0] * boostInteger.intValue() + ab[1];
		}
		*/
		
		return boostFloat;
	}
	
	private static Float getBoostFloat(DictionaryEntry dictionaryEntry) {
		// FM_FIXME: do usuniecia, a logike do przerzucenia dla odpowiednika dla Entry
		
		List<Attribute> priorityList = dictionaryEntry.getAttributeList().getAttributeList(AttributeType.PRIORITY);
		
		Integer boostInteger = priorityList != null && priorityList.size() > 0 ? Integer.parseInt(priorityList.get(0).getAttributeValue().get(0)) : Integer.MAX_VALUE;
		
		return getBoostFloat(boostInteger);		
	}
	
	private static Float getBoostFloat(Entry entry) {
		// FM_FIXME: do implementacji
		
		MiscInfo misc = entry.getMisc();
		
		if (misc == null) {
			return null;
		}
		
		OldPolishJapaneseDictionaryInfo oldPolishJapaneseDictionary = misc.getOldPolishJapaneseDictionary();
		
		if (oldPolishJapaneseDictionary == null) {
			return getBoostFloat(Integer.MAX_VALUE);
		}
		
		OldPolishJapaneseDictionaryInfoAttributeListInfo priorityAttribute = oldPolishJapaneseDictionary.getAttributeList().stream().filter(f -> f.getType().equals(AttributeType.PRIORITY.name())).findFirst().orElse(null);
		
		if (priorityAttribute == null) {
			return getBoostFloat(Integer.MAX_VALUE);
		}
		
		Integer boostInteger = Integer.parseInt(priorityAttribute.getValue());
		
		return getBoostFloat(boostInteger);
	}
	
	private static float[] calculateAandBFactor(float x1, float y1, float x2, float y2) {		
		float a = (y2 - y1) / (x2 - x1);
		float b = y2 - a * x2;

		return new float[] { a, b };
	}
	
	private static TextField setBoost(TextField textField, Float boost) {
		
		if (boost != null) {
			textField.setBoost(boost);
		}
		
		return textField;
	}
	
	private static void addAlternativeRomaji(Document document, String fieldName, String romaji, boolean generatePrefixes, Float boostFloat) {
		
		if (romaji.contains(" ") == true) {
			
			String romajiWithoutSpace = romaji.replaceAll(" ", "");
			
			document.add(setBoost(new TextField(fieldName, romajiWithoutSpace, Field.Store.NO), boostFloat));
			
			addPrefixes(document, fieldName, romajiWithoutSpace, generatePrefixes, boostFloat);
		}
		
		if (romaji.contains("'") == true) {
			
			String romajiWithoutChar = romaji.replaceAll("'", "");
			
			document.add(setBoost(new TextField(fieldName, romajiWithoutChar, Field.Store.NO), boostFloat));
			
			addPrefixes(document, fieldName, romajiWithoutChar, generatePrefixes, boostFloat);
		}		
		
		if (romaji.contains("du") == true) {
			
			String romajiDzu = romaji.replaceAll("du", "dzu");
			
			document.add(setBoost(new TextField(fieldName, romajiDzu, Field.Store.NO), boostFloat));
			
			addPrefixes(document, fieldName, romajiDzu, generatePrefixes, boostFloat);
		}
		
		if (romaji.contains(" o ") == true) {
			
			String romajiWo = romaji.replaceAll(" o ", " wo ");
						
			document.add(setBoost(new TextField(fieldName, romajiWo, Field.Store.NO), boostFloat));
			
			if (generatePrefixes == true) {
				addPrefixes(document, fieldName, romajiWo, generatePrefixes, boostFloat);
			}
		}

		if (romaji.contains("tsu") == true) {
			
			String romajiTu = romaji.replaceAll("tsu", "tu");
			
			document.add(setBoost(new TextField(fieldName, romajiTu, Field.Store.NO), boostFloat));
			
			addPrefixes(document, fieldName, romajiTu, generatePrefixes, boostFloat);
		}

		if (romaji.contains("shi") == true) {
			
			String romajiSi = romaji.replaceAll("shi", "si");
						
			document.add(setBoost(new TextField(fieldName, romajiSi, Field.Store.NO), boostFloat));
			
			addPrefixes(document, fieldName, romajiSi, generatePrefixes, boostFloat);
		}

		if (romaji.contains("fu") == true) {
			
			String romajiHu = romaji.replaceAll("fu", "hu");
						
			document.add(setBoost(new TextField(fieldName, romajiHu, Field.Store.NO), boostFloat));
			
			addPrefixes(document, fieldName, romajiHu, generatePrefixes, boostFloat);
		}		
	}
	
	private static void addPrefixes(Document document, String fieldName, String value, boolean generatePrefixes) {
		addPrefixes(document, fieldName, value, generatePrefixes, null);
	}
	
	private static void addPrefixes(Document document, String fieldName, String value, boolean generatePrefixes, Float boostFloat) {
		
		if (generatePrefixes == false) {
			return;
		}
		
		for (int idx = 0; idx < value.length(); ++idx) {
			
			String prefix = value.substring(idx);
			
			document.add(setBoost(new TextField(fieldName + "_" + LuceneStatic.prefix, prefix, Field.Store.NO), boostFloat));			
		}
	}
	
	private static void countGrammaFormAndExamples_TO_DELETE(List<DictionaryEntry> dictionaryEntryList, IndexWriter indexWriter, boolean addGrammaAndExample, boolean addSugestionList) throws IOException {
		
		// FM_FIXME: do uzycia z word2 xml
		// FM_FIXME: do usuniecia
		
		KeigoHelper keigoHelper = new KeigoHelper();
		
		for (DictionaryEntry dictionaryEntry : dictionaryEntryList) {
			
			System.out.println(String.format("DictionaryEntry(countGrammaFormAndExamples) id = %s", dictionaryEntry.getId()));
			
			// count form for dictionary entry
			// countGrammaFormAndExamples(indexWriter, dictionaryEntry, keigoHelper, addGrammaAndExample, addSugestionList);	
		}
	}
	
	private static void countGrammaFormAndExamples(Document document, Entry word2Entry, KeigoHelper keigoHelper, boolean addGrammaAndExample, boolean addSugestionList, Float boostFloat) throws IOException {
		
		// count form for word 2 entry		
		List<KanjiKanaPair> kanjiKanaPairList = Dictionary2HelperCommon.getKanjiKanaPairListStatic(word2Entry);
		
		for (KanjiKanaPair kanjiKanaPair : kanjiKanaPairList) {
						
			// gramma form conjugate request
			GrammaFormConjugateRequest grammaFormConjugateRequest = new GrammaFormConjugateRequest(kanjiKanaPair);
			
			// example request
			ExampleRequest exampleRequest = new ExampleRequest(kanjiKanaPair);
			
			{
				// gramma form cache
				Map<GrammaFormConjugateResultType, GrammaFormConjugateResult> grammaFormCache = new HashMap<GrammaFormConjugateResultType, GrammaFormConjugateResult>();
				
				List<GrammaFormConjugateGroupTypeElements> grammaConjufateResult = GrammaConjugaterManager.getGrammaConjufateResult(keigoHelper, grammaFormConjugateRequest, grammaFormCache, null, true);
				
				if (grammaConjufateResult != null) {
					ExampleManager.getExamples(keigoHelper, exampleRequest, grammaFormCache, null, true);
				}
			}
			
			// 
			
			for (DictionaryEntryType currentDictionaryEntryType : grammaFormConjugateRequest.getDictionaryEntryTypeList()) {
				// gramma form cache
				Map<GrammaFormConjugateResultType, GrammaFormConjugateResult> grammaFormCache = new HashMap<GrammaFormConjugateResultType, GrammaFormConjugateResult>(); 
				
				List<GrammaFormConjugateGroupTypeElements> grammaConjufateResult = GrammaConjugaterManager.getGrammaConjufateResult(keigoHelper, grammaFormConjugateRequest, grammaFormCache, currentDictionaryEntryType, true);
				
				if (grammaConjufateResult != null) {
					
					List<ExampleGroupTypeElements> examples = ExampleManager.getExamples(keigoHelper, exampleRequest, grammaFormCache, currentDictionaryEntryType, true);
					
					if (addGrammaAndExample == true) {
						addGrammaFormConjugateGroupList(document, grammaConjufateResult, addSugestionList, boostFloat);
						
						if (examples != null) {
							addExampleGroupTypeList(document, examples, addSugestionList, boostFloat);
						}
					}
				}
			}
		}
	}
	
	private static void addGrammaFormConjugateGroupList(Document document, List<GrammaFormConjugateGroupTypeElements> grammaConjufateResult, boolean addSugestionList, Float boostFloat) throws IOException {
		
		if (grammaConjufateResult == null) {
			return;
		}
		
		for (GrammaFormConjugateGroupTypeElements grammaFormConjugateGroupTypeElements : grammaConjufateResult) {
						
			List<GrammaFormConjugateResult> grammaFormConjugateResults = grammaFormConjugateGroupTypeElements.getGrammaFormConjugateResults();
			
			for (GrammaFormConjugateResult grammaFormConjugateResult : grammaFormConjugateResults) {				
				addGrammaFormConjugateResult(document, grammaFormConjugateResult, addSugestionList, boostFloat);
			}
		}		
	}
	
	private static void addExampleGroupTypeList(Document document, List<ExampleGroupTypeElements> exampleGroupTypeElementsList, boolean addSugestionList, Float boostFloat) throws IOException {
		
		if (exampleGroupTypeElementsList == null) {
			return;
		}
		
		for (ExampleGroupTypeElements exampleGroupTypeElements : exampleGroupTypeElementsList) {
			
			List<ExampleResult> exampleResults = exampleGroupTypeElements.getExampleResults();
			
			for (ExampleResult currentExampleResult : exampleResults) {
				addExampleResult(document, currentExampleResult, addSugestionList, boostFloat);
			}
		}		
	}
	
	private static void addGrammaFormConjugateResult(Document document, GrammaFormConjugateResult grammaFormConjugateResult, boolean addSugestionList, Float boostFloat) throws IOException {
				
		// kanji
		if (grammaFormConjugateResult.isKanjiExists() == true) {					
			document.add(new StringField(LuceneStatic.dictionaryEntry2_grammaConjufateResult_and_exampleResult_kanji, grammaFormConjugateResult.getKanji(), Field.Store.YES));
			
			if (addSugestionList == true) {
				addSuggestion(document, LuceneStatic.dictionaryEntry_web_sugestionList, emptyIfNull(grammaFormConjugateResult.getKanji()), false);
			}
		}
				
		// kanaList
		List<String> kanaList = grammaFormConjugateResult.getKanaList();
		
		for (String currentKana : kanaList) {
			document.add(new StringField(LuceneStatic.dictionaryEntry2_grammaConjufateResult_and_exampleResult_kanaList, currentKana, Field.Store.YES));
			
			if (addSugestionList == true) {
				addSuggestion(document, LuceneStatic.dictionaryEntry_web_sugestionList, emptyIfNull(currentKana), false);
			}
		}
		
		// romajiList 
		List<String> romajiList = grammaFormConjugateResult.getRomajiList();
		
		for (String currentRomaji : romajiList) {
			document.add(setBoost(new TextField(LuceneStatic.dictionaryEntry2_grammaConjufateResult_and_exampleResult_romajiList, currentRomaji, Field.Store.YES), boostFloat));
			
			// dodanie alternatyw romaji
			addAlternativeRomaji(document, LuceneStatic.dictionaryEntry2_grammaConjufateResult_and_exampleResult_romajiList, currentRomaji, false, boostFloat);
						
			if (addSugestionList == true) {
				addSuggestion(document, LuceneStatic.dictionaryEntry_web_sugestionList, emptyIfNull(currentRomaji), false);
			}
		}
		
		if (grammaFormConjugateResult.getAlternative() != null) {
			addGrammaFormConjugateResult(document, grammaFormConjugateResult.getAlternative(), addSugestionList, boostFloat);
		}
	}
	
	private static void addExampleResult(Document document, ExampleResult exampleResult, boolean addSugestionList, Float boostFloat) throws IOException {
		
		// kanji
		if (exampleResult.isKanjiExists() == true) {					
			document.add(new StringField(LuceneStatic.dictionaryEntry2_grammaConjufateResult_and_exampleResult_kanji, exampleResult.getKanji(), Field.Store.YES));
			
			if (addSugestionList == true) {
				addSuggestion(document, LuceneStatic.dictionaryEntry_web_sugestionList, emptyIfNull(exampleResult.getKanji()), false);
			}
		}
		
		// kanaList
		List<String> kanaList = exampleResult.getKanaList();
		
		for (String currentKana : kanaList) {
			document.add(new StringField(LuceneStatic.dictionaryEntry2_grammaConjufateResult_and_exampleResult_kanaList, currentKana, Field.Store.YES));
			
			if (addSugestionList == true) {
				addSuggestion(document, LuceneStatic.dictionaryEntry_web_sugestionList, emptyIfNull(currentKana), false);
			}
		}
		
		// romajiList 
		List<String> romajiList = exampleResult.getRomajiList();
		
		for (String currentRomaji : romajiList) {
			document.add(setBoost(new TextField(LuceneStatic.dictionaryEntry2_grammaConjufateResult_and_exampleResult_romajiList, currentRomaji, Field.Store.YES), boostFloat));
			
			// dodanie alternatyw romaji
			addAlternativeRomaji(document, LuceneStatic.dictionaryEntry2_grammaConjufateResult_and_exampleResult_romajiList, currentRomaji, false, boostFloat);
			
			if (addSugestionList == true) {
				addSuggestion(document, LuceneStatic.dictionaryEntry_web_sugestionList, emptyIfNull(currentRomaji), false);
			}
		}
		
		if (exampleResult.getAlternative() != null) {
			addExampleResult(document, exampleResult.getAlternative(), addSugestionList, boostFloat);
		}		
	}
	
	private static void readSentenceFile(IndexWriter indexWriter, FileInputStream sentencesInputStream) throws IOException {
		
		CsvReader csvReader = new CsvReader(new InputStreamReader(sentencesInputStream), ',');
		
		while (csvReader.readRecord()) {
			
			String id = csvReader.get(0);
			String lang = csvReader.get(1);
			String sentence = csvReader.get(2);
			
			System.out.println(String.format("TatoebaSentence id = %s", id));
			
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
			
			List<String> sentenceIdList = Utils.parseStringIntoList(sentenceIdListString /*, false */);
			
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
	
	/*
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
	*/

	private static void readKanjiDictionaryFile(IndexWriter indexWriter, /* List<RadicalInfo> radicalInfoList, */
			InputStream kanjiInputStream, boolean addSugestionList, boolean generatePrefixes) throws IOException, DictionaryException, SQLException, JAXBException {

		/*
		Map<String, RadicalInfo> radicalListMapCache = new HashMap<String, RadicalInfo>();

		for (RadicalInfo currentRadicalInfo : radicalInfoList) {

			String radical = currentRadicalInfo.getRadical();

			radicalListMapCache.put(radical, currentRadicalInfo);
		}
		*/
				
		// wczytanie pliku kanji.xml
		Set<String> allAvailableRadicalSet = new HashSet<String>();
		
		//
		
		JAXBContext jaxbContext = JAXBContext.newInstance(Kanjidic2.class);              

		Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
		
		Kanjidic2 kanjidic2 = (Kanjidic2) jaxbUnmarshaller.unmarshal(kanjiInputStream);
			
		// chodzimy po znakach kanji i je zapisujemy
		for (KanjiCharacterInfo kanjiCharacterInfo : kanjidic2.getCharacterList()) {			
			System.out.println("Add kanji dic 2 entry: " + kanjiCharacterInfo.getKanji() + " (" + kanjiCharacterInfo.getId() + ")");			
			
			// update radical info
			if (kanjiCharacterInfo.getMisc2() != null) {
				//updateRadicalInfoUse(radicalListMapCache, entry.getKanjiDic2Entry().getRadicals());
				
				allAvailableRadicalSet.addAll(kanjiCharacterInfo.getMisc2().getRadicals());
			}
			
			// usuniecie zbednych elementow z kanji w celach optymalizacyjnych
			kanjiCharacterInfo.setCodePoint(null);
			kanjiCharacterInfo.setDictionaryNumber(null);
			kanjiCharacterInfo.setQueryCode(null);			
			
			// add
			addKanjiEntry(indexWriter, kanjiCharacterInfo, addSugestionList, generatePrefixes);
		}
		
		// add available radical list
		addAvailableRadicalList(indexWriter, allAvailableRadicalSet);
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
	
	private static void addKanjiEntry(IndexWriter indexWriter, KanjiCharacterInfo kanjiCharacterInfo, boolean addSugestionList, boolean generatePrefixes) throws IOException {

		Gson gson = new Gson();
		
		//
		
		Document document = new Document();
		
		// object type
		document.add(new StringField(LuceneStatic.objectType, LuceneStatic.kanjiEntry_objectType, Field.Store.YES));
		
		// id
		document.add(new IntField(LuceneStatic.kanjiEntry_id, kanjiCharacterInfo.getId(), Field.Store.YES));

		// kanji
		document.add(new StringField(LuceneStatic.kanjiEntry_kanji, emptyIfNull(kanjiCharacterInfo.getKanji()), Field.Store.YES));
		
		addPrefixes(document, LuceneStatic.kanjiEntry_kanji, emptyIfNull(kanjiCharacterInfo.getKanji()), generatePrefixes);
		
		if (addSugestionList == true) {
			
			addSuggestion(document, LuceneStatic.kanjiEntry_android_sugestionList, emptyIfNull(kanjiCharacterInfo.getKanji()), false);
			addSuggestion(document, LuceneStatic.kanjiEntry_web_sugestionList, emptyIfNull(kanjiCharacterInfo.getKanji()), false);
			
			addSpellChecker(document, LuceneStatic.kanjiEntry_android_spellCheckerList, emptyIfNull(kanjiCharacterInfo.getKanji()));
			addSpellChecker(document, LuceneStatic.kanjiEntry_web_spellCheckerList, emptyIfNull(kanjiCharacterInfo.getKanji()));
		}
			
		// polishTranslatesList
		List<String> polishtranslatesList = Utils.getPolishTranslates(kanjiCharacterInfo);
		
		for (String currentTranslate : polishtranslatesList) {
			
			document.add(new TextField(LuceneStatic.kanjiEntry_polishTranslatesList, currentTranslate, Field.Store.YES));
			
			addPrefixes(document, LuceneStatic.kanjiEntry_polishTranslatesList, currentTranslate, generatePrefixes);
			
			if (addSugestionList == true) {
								
				addSuggestion(document, LuceneStatic.kanjiEntry_android_sugestionList, currentTranslate, true);
				addSuggestion(document, LuceneStatic.kanjiEntry_web_sugestionList, currentTranslate, true);
				
				addSpellChecker(document, LuceneStatic.kanjiEntry_android_spellCheckerList, currentTranslate);
				addSpellChecker(document, LuceneStatic.kanjiEntry_web_spellCheckerList, currentTranslate);				
			}
			
			String currentTranslateWithoutPolishChars = Utils.removePolishChars(currentTranslate);
				
			document.add(new TextField(LuceneStatic.kanjiEntry_infoWithoutPolishChars, currentTranslateWithoutPolishChars, Field.Store.NO));
			
			addPrefixes(document, LuceneStatic.kanjiEntry_infoWithoutPolishChars, currentTranslateWithoutPolishChars, generatePrefixes);
		}
		
		// info
		String info = emptyIfNull(Utils.getPolishAdditionalInfo(kanjiCharacterInfo));
		
		document.add(new TextField(LuceneStatic.kanjiEntry_info, info, Field.Store.YES));
		
		addPrefixes(document, LuceneStatic.kanjiEntry_info, info, generatePrefixes);
		
		// infoWithoutPolishChars
		String infoWithoutPolishChars = Utils.removePolishChars(info);
			
		document.add(new TextField(LuceneStatic.kanjiEntry_infoWithoutPolishChars, infoWithoutPolishChars, Field.Store.NO));
		
		addPrefixes(document, LuceneStatic.kanjiEntry_infoWithoutPolishChars, infoWithoutPolishChars, generatePrefixes);

		// strokeCount
		document.add(new IntField(LuceneStatic.kanjiEntry_strokeCount, kanjiCharacterInfo.getMisc().getStrokeCountList().get(0), Field.Store.YES));
		
		// used
		document.add(new StringField(LuceneStatic.kanjiEntry_used, String.valueOf(kanjiCharacterInfo.getMisc2().isUsed()), Field.Store.YES));
		
		// kanjiDic2Entry_radicalsList
		List<String> radicalsList = kanjiCharacterInfo.getMisc2().getRadicals();
		
		for (String currentRadical : radicalsList) {
			document.add(new StringField(LuceneStatic.kanjiEntry_radicalsList, currentRadical, Field.Store.YES));
		}
		
		// strokePaths - wynosimy z xml-a w celach optymalizacyjnych
		List<String> strokePaths = new ArrayList<>();
		
		strokePaths.addAll(kanjiCharacterInfo.getMisc2().getStrokePaths());
		
		kanjiCharacterInfo.getMisc2().getStrokePaths().clear();
		
		// xml
		document.add(new StoredField(LuceneStatic.kanjiEntry_entry, gson.toJson(kanjiCharacterInfo)));	

		indexWriter.addDocument(document);
		
		// dodanie strokePaths do osobnego dokumentu
		if (strokePaths.size() > 0) {
			Document strokePathsDocument = new Document();
			
			// object type
			strokePathsDocument.add(new StringField(LuceneStatic.objectType, LuceneStatic.kanjiEntryStrokePaths_objectType, Field.Store.YES));
			
			// id
			strokePathsDocument.add(new IntField(LuceneStatic.kanjiEntryStrokePaths_id, kanjiCharacterInfo.getId(), Field.Store.YES));

			// strokePaths
			strokePathsDocument.add(new StoredField(LuceneStatic.kanjiEntryStrokePaths_strokePaths, Utils.convertListToString(strokePaths)));
			
			indexWriter.addDocument(strokePathsDocument);			
		}
	}
	
	private static List<DictionaryEntry> readNamesFile(IndexWriter indexWriter, InputStream namesInputStream, Counter idCounter, boolean addSugestionList, boolean generatePrefixes) throws IOException, DictionaryException, SQLException {
		
		List<DictionaryEntry> namesDictionaryEntryList = new ArrayList<DictionaryEntry>();

		CsvReader csvReader = new CsvReader(new InputStreamReader(namesInputStream), ',');

		while (csvReader.readRecord()) {

			DictionaryEntry entry = parseDictionaryEntry(csvReader);
			
			entry.setId(idCounter.getValue());
			
			idCounter.increment();
			
			System.out.println(String.format("DictionaryEntry (name) id = %s", entry.getId()));

			addNameDictionaryEntry(indexWriter, entry, addSugestionList, generatePrefixes);
			
			namesDictionaryEntryList.add(entry);			
		}

		csvReader.close();
		
		return namesDictionaryEntryList;
	}
	
	private static void addNameDictionaryEntry(IndexWriter indexWriter, DictionaryEntry dictionaryEntry, boolean addSugestionList, boolean generatePrefixes) throws IOException {
		
		Document document = new Document();
		
		// object type
		document.add(new StringField(LuceneStatic.objectType, LuceneStatic.nameDictionaryEntry_objectType, Field.Store.YES));
		
		// id
		document.add(new IntField(LuceneStatic.nameDictionaryEntry_id, dictionaryEntry.getId(), Field.Store.YES));
		
		// unique key
		String uniqueKey = dictionaryEntry.getUniqueKey();
		
		if (uniqueKey != null) {
			document.add(new StringField(LuceneStatic.nameDictionaryEntry_uniqueKey, uniqueKey, Field.Store.YES));
		}
		
		// dictionary entry type list
		List<String> dictionaryEntryTypeStringList = DictionaryEntryType.convertToValues(dictionaryEntry.getDictionaryEntryTypeList());
		
		for (String dictionaryEntryTypeString : dictionaryEntryTypeStringList) {
			document.add(new StringField(LuceneStatic.nameDictionaryEntry_dictionaryEntryTypeList, dictionaryEntryTypeString, Field.Store.YES));
		}
		
		// attributeList
		List<String> attributeStringList = dictionaryEntry.getAttributeList().convertAttributeListToListString();
		
		for (String currentAttribute : attributeStringList) {
			document.add(new StringField(LuceneStatic.nameDictionaryEntry_attributeList, currentAttribute, Field.Store.YES));
		}
				
		// kanji
		document.add(new StringField(LuceneStatic.nameDictionaryEntry_kanji, emptyIfNull(dictionaryEntry.getKanji()), Field.Store.YES));
		
		addPrefixes(document, LuceneStatic.nameDictionaryEntry_kanji, emptyIfNull(dictionaryEntry.getKanji()), generatePrefixes);
		
		if (addSugestionList == true) {
			addSuggestion(document, LuceneStatic.dictionaryEntry_web_sugestionList, emptyIfNull(dictionaryEntry.getKanji()), false);
		}
		
		// kanaList
		String kana = dictionaryEntry.getKana();
		
		document.add(new StringField(LuceneStatic.nameDictionaryEntry_kana, kana, Field.Store.YES));
		
		addPrefixes(document, LuceneStatic.nameDictionaryEntry_kana, kana, generatePrefixes);
		
		if (addSugestionList == true) {
			addSuggestion(document, LuceneStatic.dictionaryEntry_web_sugestionList, kana, false);
		}
				
		// romajiList
		String romaji = dictionaryEntry.getRomaji();
		
		document.add(new TextField(LuceneStatic.nameDictionaryEntry_romaji, romaji, Field.Store.YES));
		
		addPrefixes(document, LuceneStatic.nameDictionaryEntry_romaji, romaji, generatePrefixes);
		
		addAlternativeRomaji(document, LuceneStatic.nameDictionaryEntry_virtual_romaji, romaji, generatePrefixes, null);
				
		if (addSugestionList == true) {
			addSuggestion(document, LuceneStatic.dictionaryEntry_web_sugestionList, romaji, false);
		}
		
		// translatesList
		List<String> translates = dictionaryEntry.getTranslates();
		
		for (String currentTranslate : translates) {
			
			document.add(new TextField(LuceneStatic.nameDictionaryEntry_translatesList, currentTranslate, Field.Store.YES));
			
			addPrefixes(document, LuceneStatic.nameDictionaryEntry_translatesList, currentTranslate, generatePrefixes);
			
			if (addSugestionList == true) {
								
				addSuggestion(document, LuceneStatic.dictionaryEntry_web_sugestionList, currentTranslate, true);
			}
			
			String currentTranslateWithoutPolishChars = Utils.removePolishChars(currentTranslate);
				
			document.add(new TextField(LuceneStatic.nameDictionaryEntry_translatesListWithoutPolishChars, currentTranslateWithoutPolishChars, Field.Store.NO));
			
			addPrefixes(document, LuceneStatic.nameDictionaryEntry_translatesListWithoutPolishChars, currentTranslateWithoutPolishChars, generatePrefixes);
		}
		
		// info
		String info = emptyIfNull(dictionaryEntry.getInfo());
		
		document.add(new TextField(LuceneStatic.nameDictionaryEntry_info, info, Field.Store.YES));
		
		addPrefixes(document, LuceneStatic.nameDictionaryEntry_info, info, generatePrefixes);
			
		String infoWithoutPolishChars = Utils.removePolishChars(info);
			
		document.add(new TextField(LuceneStatic.nameDictionaryEntry_infoWithoutPolishChars, infoWithoutPolishChars, Field.Store.NO));
		
		addPrefixes(document, LuceneStatic.nameDictionaryEntry_infoWithoutPolishChars, infoWithoutPolishChars, generatePrefixes);
				
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
	
	private static void generateSpellChecker(File dbDir, LuceneAnalyzer analyzer, boolean addSugestionList) throws IOException {
		
		if (addSugestionList == true) {
			
			Directory mainIndex = FSDirectory.open(dbDir);			
			IndexReader mainIndexReader = DirectoryReader.open(mainIndex);
			
			//
			
			List<LuceneDatabaseSuggesterAndSpellCheckerSource> luceneDatabaseSuggesterAndSpellCheckerSourceList = new ArrayList<LuceneDatabaseSuggesterAndSpellCheckerSource>();
			
			luceneDatabaseSuggesterAndSpellCheckerSourceList.add(LuceneDatabaseSuggesterAndSpellCheckerSource.DICTIONARY_ENTRY_WEB);
			luceneDatabaseSuggesterAndSpellCheckerSourceList.add(LuceneDatabaseSuggesterAndSpellCheckerSource.DICTIONARY_ENTRY_ANDROID);
			
			luceneDatabaseSuggesterAndSpellCheckerSourceList.add(LuceneDatabaseSuggesterAndSpellCheckerSource.KANJI_ENTRY_WEB);
			luceneDatabaseSuggesterAndSpellCheckerSourceList.add(LuceneDatabaseSuggesterAndSpellCheckerSource.KANJI_ENTRY_ANDROID);

			for (LuceneDatabaseSuggesterAndSpellCheckerSource luceneDatabaseSuggesterAndSpellCheckerSource : luceneDatabaseSuggesterAndSpellCheckerSourceList) {
				
				// tworzenie podindeksu lucene
				File subDbOutDirFile = new File(dbDir, "subindex_" + luceneDatabaseSuggesterAndSpellCheckerSource.getSpellCheckerListFieldName());
				
				if (subDbOutDirFile.isDirectory() == true) {
					
					File[] dbOutDirFileListFiles = subDbOutDirFile.listFiles();
					
					for (File file : dbOutDirFileListFiles) {
						file.delete();
					}
				}
				
				// tworzenie indeksu lucene
				Directory subIndex = FSDirectory.open(subDbOutDirFile);
				
				// wypelnianie podindeksu
				initializeSpellChecker(subIndex, mainIndexReader, analyzer, luceneDatabaseSuggesterAndSpellCheckerSource);
				
				subIndex.close();
			}

			//
			
			mainIndexReader.close();
			mainIndex.close();
		}
	}
	
	private static void initializeSpellChecker(Directory index, IndexReader reader, LuceneAnalyzer analyzer, LuceneDatabaseSuggesterAndSpellCheckerSource source) throws IOException {
		
		System.out.println("Spell Checker: " + source);
		
		// UWAGA: Podobna metoda jest w klasie LuceneDatabase.initializeSpellChecker
		
		LuceneDictionary luceneDictionary = new LuceneDictionary(reader, source.getSpellCheckerListFieldName());
		
		SpellChecker spellChecker = new SpellChecker(index, new JaroWinklerDistance());
				
		IndexWriterConfig indexWriterConfig = new IndexWriterConfig(Version.LUCENE_47, analyzer);
		
		spellChecker.indexDictionary(luceneDictionary, indexWriterConfig, false);
		
		spellChecker.close();
	}	
		
	private static void cacheSuggester(File dbDir, LuceneAnalyzer analyzer, boolean addSugestionList) throws IOException {
				
		if (addSugestionList == true) {
			
			Directory mainIndex = FSDirectory.open(dbDir);
			IndexReader mainIndexReader = DirectoryReader.open(mainIndex);
			
			//
			
			List<LuceneDatabaseSuggesterAndSpellCheckerSource> luceneDatabaseSuggesterAndSpellCheckerSourceList = new ArrayList<LuceneDatabaseSuggesterAndSpellCheckerSource>();
			
			luceneDatabaseSuggesterAndSpellCheckerSourceList.add(LuceneDatabaseSuggesterAndSpellCheckerSource.DICTIONARY_ENTRY_WEB);
			luceneDatabaseSuggesterAndSpellCheckerSourceList.add(LuceneDatabaseSuggesterAndSpellCheckerSource.DICTIONARY_ENTRY_ANDROID);
			
			luceneDatabaseSuggesterAndSpellCheckerSourceList.add(LuceneDatabaseSuggesterAndSpellCheckerSource.KANJI_ENTRY_WEB);
			luceneDatabaseSuggesterAndSpellCheckerSourceList.add(LuceneDatabaseSuggesterAndSpellCheckerSource.KANJI_ENTRY_ANDROID);

			for (LuceneDatabaseSuggesterAndSpellCheckerSource luceneDatabaseSuggesterAndSpellCheckerSource : luceneDatabaseSuggesterAndSpellCheckerSourceList) {
				
				// cache'owanie podpowiadacza
				System.out.println("Suggester cache: " + luceneDatabaseSuggesterAndSpellCheckerSource);
				
				// UWAGA: Podobna metoda jest w klasie LuceneDatabase.initializeSuggester
				LuceneDictionary luceneDictionary = new LuceneDictionary(mainIndexReader, luceneDatabaseSuggesterAndSpellCheckerSource.getSuggestionListFieldName());
				
				AnalyzingSuggester lookup = new AnalyzingSuggester(analyzer);
				
				lookup.build(luceneDictionary);
				
				// zapis do pliku
				lookup.store(new FileOutputStream(new File(dbDir, luceneDatabaseSuggesterAndSpellCheckerSource.getSuggesterCacheFileName())));
			}

			//
			
			mainIndexReader.close();
			mainIndex.close();
		}
	}
	
	private static void addWord2Xml(IndexWriter indexWriter, String word2XmlFilePath, boolean addGrammaAndExample, boolean addSugestionList, boolean generatePrefixes) throws JAXBException, IOException {
				
		JAXBContext jaxbContext = JAXBContext.newInstance(JMdict.class);              

		Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
		
		JMdict jmdict = (JMdict) jaxbUnmarshaller.unmarshal(new File(word2XmlFilePath));
		
		//
		
		Gson gson = new Gson();
		KeigoHelper keigoHelper = new KeigoHelper();
		
		//

		// pobranie listy wpisow
		List<Entry> entryList = jmdict.getEntryList();

		for (Entry entry : entryList) {
			
			System.out.println("Add word 2 entry: " + entry.getEntryId());
			
			// wyliczenie boost'era
			Float boostFloat = getBoostFloat(entry);
															
			// dodanie do lucynki
			Document document = new Document();
			
			// object type
			document.add(new StringField(LuceneStatic.objectType, LuceneStatic.dictionaryEntry2_objectType, Field.Store.YES));

			// id
			document.add(new IntField(LuceneStatic.dictionaryEntry2_id, entry.getEntryId(), Field.Store.YES));
			
			// xml
			document.add(new StoredField(LuceneStatic.dictionaryEntry2_entry, gson.toJson(entry)));
			
			// FM_FIXME: sprawdzic, czy to dobrze i czy dziala poprawnie cala funkcjonalnosc
			// ze wszystkie kanji, kana i tlumaczenia sa w jednym dokumencie			
			
			List<KanjiInfo> kanjiInfoList = entry.getKanjiInfoList();
			
			// kanji
			for (KanjiInfo kanjiInfo : kanjiInfoList) {
				String kanji = kanjiInfo.getKanji();
				
				document.add(new StringField(LuceneStatic.dictionaryEntry2_kanji, emptyIfNull(kanji), Field.Store.YES));
				
				addPrefixes(document, LuceneStatic.dictionaryEntry2_kanji, emptyIfNull(kanji), generatePrefixes, boostFloat);
				
				if (addSugestionList == true) {					
					addSuggestion(document, LuceneStatic.dictionaryEntry_android_sugestionList, emptyIfNull(kanji), false);
					addSuggestion(document, LuceneStatic.dictionaryEntry_web_sugestionList, emptyIfNull(kanji), false);
					
					addSpellChecker(document, LuceneStatic.dictionaryEntry_android_spellCheckerList, emptyIfNull(kanji));
					addSpellChecker(document, LuceneStatic.dictionaryEntry_web_spellCheckerList, emptyIfNull(kanji));
				}				
			}
			
			List<ReadingInfo> readingInfoList = entry.getReadingInfoList();
			
			for (ReadingInfo readingInfo : readingInfoList) {
				// kanaList
				String kana = readingInfo.getKana().getValue();
				
				document.add(new StringField(LuceneStatic.dictionaryEntry2_kana, kana, Field.Store.YES));
				
				addPrefixes(document, LuceneStatic.dictionaryEntry2_kana, kana, generatePrefixes, boostFloat);
				
				if (addSugestionList == true) {
					addSuggestion(document, LuceneStatic.dictionaryEntry_android_sugestionList, kana, false);
					addSuggestion(document, LuceneStatic.dictionaryEntry_web_sugestionList, kana, false);
					
					addSpellChecker(document, LuceneStatic.dictionaryEntry_android_spellCheckerList, kana);
					addSpellChecker(document, LuceneStatic.dictionaryEntry_web_spellCheckerList, kana);
				}
				
				// romajiList
				String romaji = readingInfo.getKana().getRomaji();
				
				if (romaji != null) {
					// INFO: normalnie romaji zawsze powinno istniec, ale gdy slownik nie jest w pelni poprawny
					// czyli nie przeszedl calej walidacji to moze zdarzyc sie
					// ale podczas generowania slownika z wydaniem to nigdy nie powinno zdarzyc sie
					
					document.add(setBoost(new TextField(LuceneStatic.dictionaryEntry2_romaji, romaji, Field.Store.YES), boostFloat));
	
					addPrefixes(document, LuceneStatic.dictionaryEntry2_romaji, romaji, generatePrefixes, boostFloat);
					
					// dodanie alternatyw romaji
					addAlternativeRomaji(document, LuceneStatic.dictionaryEntry2_romaji, romaji, generatePrefixes, boostFloat);
					
					if (addSugestionList == true) {					
						addSuggestion(document, LuceneStatic.dictionaryEntry_android_sugestionList, romaji, false);
						addSuggestion(document, LuceneStatic.dictionaryEntry_web_sugestionList, romaji, false);
						
						addSpellChecker(document, LuceneStatic.dictionaryEntry_android_spellCheckerList, romaji);
						addSpellChecker(document, LuceneStatic.dictionaryEntry_web_spellCheckerList, romaji);			
					}
				}
			}
			
			List<Sense> senseList = entry.getSenseList();
			
			for (Sense sense : senseList) {
				
				List<Gloss> polishGlossList = Dictionary2HelperCommon.getPolishGlossList(sense.getGlossList());
				
				for (Gloss gloss : polishGlossList) {
					// translatesList
					String currentTranslate = gloss.getValue();
					
					document.add(setBoost(new TextField(LuceneStatic.dictionaryEntry2_translatesList, currentTranslate, Field.Store.YES), boostFloat));
					
					addPrefixes(document, LuceneStatic.dictionaryEntry2_translatesList, currentTranslate, generatePrefixes, boostFloat);
					
					if (addSugestionList == true) {
						addSuggestion(document, LuceneStatic.dictionaryEntry_android_sugestionList, currentTranslate, true);
						addSuggestion(document, LuceneStatic.dictionaryEntry_web_sugestionList, currentTranslate, true);
						
						addSpellChecker(document, LuceneStatic.dictionaryEntry_android_spellCheckerList, currentTranslate);
						addSpellChecker(document, LuceneStatic.dictionaryEntry_web_spellCheckerList, currentTranslate);
					}
					
					String currentTranslateWithoutPolishChars = Utils.removePolishChars(currentTranslate);
						
					document.add(setBoost(new TextField(LuceneStatic.dictionaryEntry2_translatesList, currentTranslateWithoutPolishChars, Field.Store.NO), boostFloat));
					
					addPrefixes(document, LuceneStatic.dictionaryEntry2_translatesList, currentTranslateWithoutPolishChars, generatePrefixes, boostFloat);
					
					// info
					SenseAdditionalInfo firstPolishAdditionalInfo = Dictionary2HelperCommon.findFirstPolishAdditionalInfo(sense.getAdditionalInfoList());
					
					if (firstPolishAdditionalInfo != null) {
						String info = firstPolishAdditionalInfo.getValue();
						
						if (info != null) {
							document.add(setBoost(new TextField(LuceneStatic.dictionaryEntry2_info, info, Field.Store.YES), boostFloat));
							
							addPrefixes(document, LuceneStatic.dictionaryEntry2_info, info, generatePrefixes, boostFloat);
								
							String infoWithoutPolishChars = Utils.removePolishChars(info);
								
							document.add(setBoost(new TextField(LuceneStatic.dictionaryEntry2_info, infoWithoutPolishChars, Field.Store.NO), boostFloat));
							
							addPrefixes(document, LuceneStatic.dictionaryEntry2_info, infoWithoutPolishChars, generatePrefixes, boostFloat);						
						}
					}
				}				
			}
			
			// unique key
			if (entry.getMisc() != null && entry.getMisc().getOldPolishJapaneseDictionary() != null && entry.getMisc().getOldPolishJapaneseDictionary().getEntries() != null) {
				List<OldPolishJapaneseDictionaryInfoEntriesInfo> oldPolishJapaneseDictionaryInfoEntriesList = entry.getMisc().getOldPolishJapaneseDictionary().getEntries();
				
				for (OldPolishJapaneseDictionaryInfoEntriesInfo oldPolishJapaneseDictionaryInfoEntriesInfo : oldPolishJapaneseDictionaryInfoEntriesList) {
					String uniqueKey = oldPolishJapaneseDictionaryInfoEntriesInfo.getUniqueKey();
					
					if (uniqueKey != null) {
						document.add(new StringField(LuceneStatic.dictionaryEntry2_uniqueKey, uniqueKey, Field.Store.YES));
					}					
				}
			}
						
			// dictionary entry type list
			if (entry.getMisc() != null && entry.getMisc().getOldPolishJapaneseDictionary() != null && entry.getMisc().getOldPolishJapaneseDictionary().getEntries() != null) {
				List<OldPolishJapaneseDictionaryInfoEntriesInfo> oldPolishJapaneseDictionaryInfoEntriesList = entry.getMisc().getOldPolishJapaneseDictionary().getEntries();
				
				LinkedHashSet<String> allDictionaryEntryTypeStringList = new LinkedHashSet<String>(); 
				
				for (OldPolishJapaneseDictionaryInfoEntriesInfo oldPolishJapaneseDictionaryInfoEntriesInfo : oldPolishJapaneseDictionaryInfoEntriesList) {
					allDictionaryEntryTypeStringList.addAll(Arrays.asList(oldPolishJapaneseDictionaryInfoEntriesInfo.getDictionaryEntryTypeList().split(",")).stream().collect(Collectors.toList()));					
				}
								
				for (String dictionaryEntryTypeString : allDictionaryEntryTypeStringList) {
					document.add(new StringField(LuceneStatic.dictionaryEntry2_dictionaryEntryTypeList, dictionaryEntryTypeString, Field.Store.YES));
				}				
			}
											
			// attributeList
			// FM_FIXME: sprawdzic, czy to dziala
			if (entry.getMisc() != null && entry.getMisc().getOldPolishJapaneseDictionary() != null && entry.getMisc().getOldPolishJapaneseDictionary().getAttributeList() != null) {
				List<OldPolishJapaneseDictionaryInfoAttributeListInfo> attributeList = entry.getMisc().getOldPolishJapaneseDictionary().getAttributeList();
				
				for (OldPolishJapaneseDictionaryInfoAttributeListInfo attribute : attributeList) {
					String dbValue = attribute.getType();
					
					if (attribute.getValue() != null) {
						dbValue += " " + attribute.getValue();
					}
					
					document.add(new StringField(LuceneStatic.dictionaryEntry2_attributeList, dbValue, Field.Store.YES));
				}
			}
						
			// groupsList
			if (entry.getMisc() != null && entry.getMisc().getOldPolishJapaneseDictionary() != null && entry.getMisc().getOldPolishJapaneseDictionary().getGroupsList() != null) {
				List<String> groupsList = entry.getMisc().getOldPolishJapaneseDictionary().getGroupsList();

				for (String currentGroup : groupsList) {
					document.add(new StringField(LuceneStatic.dictionaryEntry2_groupsList, currentGroup, Field.Store.YES));
				}
			}
			
			// add grammas and examples			
			countGrammaFormAndExamples(document, entry, keigoHelper, addGrammaAndExample, addSugestionList, boostFloat);
						
//			// prefixKana
//			document.add(new StringField(LuceneStatic.dictionaryEntry_prefixKana, emptyIfNull(dictionaryEntry.getPrefixKana()), Field.Store.YES));
//						
//			// prefixRomaji
//			document.add(new StringField(LuceneStatic.dictionaryEntry_prefixRomaji, emptyIfNull(dictionaryEntry.getPrefixRomaji()), Field.Store.YES));
//			
//			// example sentence groupIds - FM_FIXME: do przeniesienia - to chyba nie bedzie przenoszone
//			List<String> exampleSentenceGroupIdsList = dictionaryEntry.getExampleSentenceGroupIdsList();
//					
//			for (String currentExampleSenteceGroupId : exampleSentenceGroupIdsList) {
//				document.add(new TextField(LuceneStatic.dictionaryEntry_exampleSentenceGroupIdsList, currentExampleSenteceGroupId, Field.Store.YES));
//			}

			//
			
			indexWriter.addDocument(document);
		}		
	}
		
	private static String emptyIfNull(String text) {
		if (text == null) {
			return "";
		}

		return text;
	}
	
	private static void addSuggestion(Document document, String fieldName, String fieldValue, boolean isPolishTranslate) { 
		
		if (isPolishTranslate == false) {
			
			document.add(new StringField(fieldName, fieldValue, Field.Store.YES));
			
		} else {
			
			document.add(new StringField(fieldName, fieldValue, Field.Store.YES));
			
			/*
			String fieldValueWithoutPolishChars = Utils.removePolishChars(fieldValue);
			
			if (fieldValue.equals(fieldValueWithoutPolishChars) == false) {
				document.add(new StringField(fieldName, fieldValueWithoutPolishChars + " ___ " / * LuceneDatabaseSuggesterAndSpellCheckerSource.SUGGESTER_SEPARATOR * / + fieldValue, Field.Store.YES));
			}
			*/
		}
	}
	
	private static void addSpellChecker(Document document, String fieldName, String fieldValue) { 		
		document.add(new StringField(fieldName, fieldValue, Field.Store.YES));
	}
	
	private static class Counter {
		
		private int value;
		
		public Counter(int value) {
			this.value = value;
		}
		
		public void increment() {
			value++;
		}

		public int getValue() {
			return value;
		}
	}
}
