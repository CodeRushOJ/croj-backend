UPDATE `t_problem_version` pv
JOIN `t_problem` p ON p.`id` = pv.`problem_id`
SET
  pv.`statement_json` = CASE
    WHEN JSON_CONTAINS_PATH(pv.`statement_json`, 'one', '$.source') = 0
      THEN JSON_SET(pv.`statement_json`, '$.source', p.`source`)
    ELSE pv.`statement_json`
  END,
  pv.`judge_config_json` = CASE
    WHEN JSON_CONTAINS_PATH(pv.`judge_config_json`, 'one', '$.difficulty') = 0
      THEN JSON_SET(pv.`judge_config_json`, '$.difficulty', p.`difficulty`)
    ELSE pv.`judge_config_json`
  END
WHERE JSON_CONTAINS_PATH(pv.`statement_json`, 'one', '$.source') = 0
   OR JSON_CONTAINS_PATH(pv.`judge_config_json`, 'one', '$.difficulty') = 0;
