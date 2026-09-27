JumpText = {}
local this = JumpText;

local jumpTexts = {};

function JumpText.Destroy()
  poolMgr:DestroyPool("BaseJumpText");
end

function JumpText.Initialize()
  CGResourceManager.Load(
    "BaseJumpText",
    "",
    function(assetName, asset)
      poolMgr:CreatePool("BaseJumpText", asset);
    end
  );
end

function JumpText.Update()
  for k, v in pairs(jumpTexts) do
    v.transform.localPosition = v.transform.localPosition + (Vector3.up * v.speed * CGTimer.deltaTime);

    if v.expireTime < CGTimer.time then
      poolMgr:Release("BaseJumpText", v.gameObject);
      table.remove(jumpTexts, k);
    end
  end
end

function JumpText.Show(text, parent, offsetX, offsetY, speed, duration)
  if text == nil or text == "" then return end
  if parent == nil then return end
  if offsetX == nil then offsetX = 0 end
  if offsetY == nil then offsetY = 0 end
  if speed == nil then speed = 60 end
  if duration == nil then duration = 1 end

  local gameObject = poolMgr:Get("BaseJumpText");

  if gameObject == nil then return end

  local jumpText = {};
  jumpText.speed = speed;
  jumpText.expireTime = CGTimer.time + duration;
  jumpText.gameObject = gameObject;
  jumpText.transform = gameObject.transform;
  jumpText.text = gameObject:GetComponent("Text");
  jumpText.text.alignment = TextAnchor.MiddleCenter;
  jumpText.text.fontSize = 20;
  jumpText.text.text = text;
  jumpText.transform:SetParent(parent);
  jumpText.transform.localPosition = Vector3.New(offsetX, offsetY, 0);

  table.insert(jumpTexts, jumpText);
end