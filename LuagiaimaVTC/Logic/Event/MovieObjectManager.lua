--region MovieObjectManager.lua
--[[
    功能: 演出物件管理器，讀檔，管理檔案。
]]
require "Logic/Event/MovieObject"
require "Common/Queue"

MovieList = 
{
   --"001", 檔案有誤
   "17",
   "18",
   "19",
   "10965",
   "10997",
   "11502",
   "11509",
   "11569",
   "11811",
   --"12003",檔案有誤
   "12014",
   "12031",
   "12091",
   "12093",
   "12096",
   "12403",
   "12508",
   "12589",
   "12841",
   "12861",
   "12882",
   "12921",
   "12934",
   "12939",
   "13004",
   "13014",
   "13431",
   "13444",
   "13472",
   "13483",
   "13496",
   "13513",
   "13519",
   "13821",
   "13832",
   "13885",
   "13886", 
   "14102",
   "14506",
   "14697",
   "14802",
   "14831",
   "15025",
   "15031",
   "15135",
   "15435",
   "15456",
   "15524",
   "15543",
   "15702",
   "15845",
   "15863",
   "15893",
   --"16412",  377458 Not Found
   "16551",
   "18304",
   "18405",
   "18435",
   "18506", 
   "18513",
   "18705",
   "18802",
   "18819",
   "18856",
   "18990",
   "19506",
   "19509",
   "19512",
   "19525",
   "19528",
   "19851",
   "20413",
   "20831",
   "21001",
   "21021",
   "21411",
   "21717",
   "21727",
   "21742",
   "21749",
   "21757",
   "21762",
   "21763",
   "21802",
   "21814",
   "21819",
   "21833",
   "21847",
   "21864",
   "21871",
   "21884",
   "21896",
   "23423",
   "23812",
   "24816",
   "25052",
   "25139",
   "25179",
   "25413",
   "25419",
   "25435",
   "25437",
   "25601",
   "25731",
   "25802",
   "25807",
   "25812",
   "25821",
   "25828",
   "25833",
   "26531",
   "27171",
   "28002",
   "30483",
   --"30912",   Get Jpg Fail !! 10073 Not Found
   "31001",
   "48001",
   "48005",
   "48007",
   --"48020",  Get Jpg Fail !! 50298 Not Found
   "49911",
   "49912",
   "49913",
   "49914",
   --"54825", Get Jpg Fail !! 51089 Not Found
   "56517",
   "56528",
   "57511",
   "57536",
   "59422",
   "59432",
   --"59811",  Error
   "59841",
   "60012",
   "61002",
   "61012",
   "61031",
   "61041",
   "61411",
   "61431",
   "61528",
   "61535",
   "113741",
   --"113742",   000858
   "113761",
   "120311",
   "120912",
   "130141",
   "144411",
   "144412",
   "144413",
   "154511",
   "154512",
   "154561",
   "157022",
   "184051",
   "184052",
   "210001",
   "210002",
   "217170",
   "217561",
   "217562",
   "218140",
   "218471",
   "248211",
   "248212",
   "254151",
   "254152",
   "255231",
   "257061",
   "257062",
   "257311",
   "309032",
   "594541",
   "594542",
   "598421",
   "598422",
   "604311",
   "604312",
   "1343102",
}

MovieObjectManager = {};
local this = MovieObjectManager;

local movieObjectTable = {};
local isLoadingFinish = false;

local endloadCallBack = nil;
local waitLoadMovieQ = nil;
local loadCount = 0;
local loadlist = nil;

function MovieObjectManager.Initialize() 
  waitLoadMovieQ = Queue.New();
end

function MovieObjectManager.LoadAllMovie()  
  Tlog("MovieObjectManager", "Start Loading");

  loadingMovieObject.Count = 0;
  for k, v in pairs(MovieList) do
    waitLoadMovieQ:Enqueue(v);
  end

  this.Load();
end

function MovieObjectManager.GetMovie(id, callback)
  TlogError("MovieObjectManager", string.Concat("GetMovie: ", tostring(id)));

  if movieObjectTable[movieID] ~= nil then
    if callback ~= nil then
      callback(movieObjectTable[movieID]);
    end
  else
    endloadCallBack = callback;
    waitLoadMovieQ:Enqueue(id);
    this.Load();
  end
end

function MovieObjectManager.Load()
  local count = waitLoadMovieQ.Count;
  loadCount = count;
  for i = 1, count do
    local movieID = waitLoadMovieQ:Dequeue();
    
    DataManager.ReadData(string.format("Sty/%s.sty", movieID),
      function(file)
        local reader = DatReader.New(file);

        movieObjectTable[movieID] = MovieObject.New(reader);
        
        loadCount = loadCount - 1;
        if loadCount <= 0 then
          if endloadCallBack ~= nil then
            endloadCallBack(movieObjectTable[movieID]);
            endloadCallBack = nil;
          end      
        end
      end
    );
  end
end
--endregion
