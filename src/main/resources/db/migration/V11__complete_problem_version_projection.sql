ALTER TABLE `t_problem_version`
  ADD COLUMN `projection_complete` TINYINT NOT NULL DEFAULT 0 AFTER `published_at`;

UPDATE `t_problem_version`
SET `projection_complete` = 1
WHERE JSON_CONTAINS_PATH(
        `statement_json`,
        'all',
        '$.title',
        '$.description',
        '$.inputDescription',
        '$.outputDescription',
        '$.hints',
        '$.samples',
        '$.source',
        '$.tags'
      ) = 1
  AND JSON_SCHEMA_VALID(
        '{
          "type":"object",
          "required":[
            "title","description","inputDescription","outputDescription",
            "hints","samples","source","tags"
          ],
          "properties":{
            "title":{"type":"string","minLength":1},
            "description":{"type":"string","minLength":1},
            "inputDescription":{"type":"string","minLength":1},
            "outputDescription":{"type":"string","minLength":1},
            "hints":{"type":"array","items":{"type":"string"}},
            "samples":{
              "type":"array",
              "items":{"type":"object","additionalProperties":{"type":"string"}}
            },
            "source":{"type":["string","null"]},
            "tags":{
              "type":"array",
              "items":{
                "type":"object",
                "required":["id","name","color"],
                "additionalProperties":false,
                "properties":{
                  "id":{
                    "type":"integer",
                    "minimum":1,
                    "maximum":9223372036854775807
                  },
                  "name":{"type":"string","minLength":1},
                  "color":{"type":"string","minLength":1}
                }
              }
            }
          }
        }',
        `statement_json`
      ) = 1
  AND (
        SELECT COUNT(*)
        FROM JSON_TABLE(
          `statement_json`,
          '$.tags[*]' COLUMNS(tag_id DECIMAL(20,0) PATH '$.id')
        ) AS `all_snapshot_tags`
      ) = (
        SELECT COUNT(DISTINCT `tag_id`)
        FROM JSON_TABLE(
          `statement_json`,
          '$.tags[*]' COLUMNS(tag_id DECIMAL(20,0) PATH '$.id')
        ) AS `distinct_snapshot_tags`
      )
  AND JSON_CONTAINS_PATH(
        `limits_json`,
        'all',
        '$.timeLimit',
        '$.memoryLimit',
        '$.totalScore'
      ) = 1
  AND JSON_SCHEMA_VALID(
        '{
          "type":"object",
          "required":["timeLimit","memoryLimit","totalScore"],
          "properties":{
            "timeLimit":{
              "type":"integer","minimum":1,"maximum":2147483647
            },
            "memoryLimit":{
              "type":"integer","minimum":1,"maximum":2147483647
            },
            "totalScore":{
              "type":["integer","null"],"minimum":0,"maximum":2147483647
            }
          }
        }',
        `limits_json`
      ) = 1
  AND JSON_CONTAINS_PATH(
        `judge_config_json`,
        'all',
        '$.specialJudge',
        '$.specialJudgeCode',
        '$.specialJudgeLanguage',
        '$.judgeMode',
        '$.checker',
        '$.difficulty'
      ) = 1
  AND JSON_SCHEMA_VALID(
        '{
          "type":"object",
          "required":[
            "specialJudge","specialJudgeCode","specialJudgeLanguage",
            "judgeMode","checker","difficulty"
          ],
          "properties":{
            "specialJudge":{"type":"boolean"},
            "specialJudgeCode":{"type":["string","null"]},
            "specialJudgeLanguage":{"type":["string","null"]},
            "judgeMode":{"type":"integer","enum":[0,1]},
            "checker":{"type":"string","enum":["exact","token"]},
            "difficulty":{"type":"integer","enum":[1,2,3]}
          }
        }',
        `judge_config_json`
      ) = 1;

UPDATE `t_problem` p
JOIN `t_problem_version` pv ON pv.`id` = p.`published_version_id`
SET
  p.`published_version_id` = NULL,
  p.`status` = 1
WHERE pv.`projection_complete` = 0;
