-- A clean production schema must support forum post creation without the dev profile.
-- Preserve operator-customized rows when a known slug already exists.
INSERT INTO `t_forum_category` (`name`, `slug`, `sort_order`)
SELECT '公告', 'announcements', 10
WHERE NOT EXISTS (
  SELECT 1 FROM `t_forum_category` WHERE `slug` = 'announcements'
);

INSERT INTO `t_forum_category` (`name`, `slug`, `sort_order`)
SELECT '算法交流', 'algorithms', 20
WHERE NOT EXISTS (
  SELECT 1 FROM `t_forum_category` WHERE `slug` = 'algorithms'
);

INSERT INTO `t_forum_category` (`name`, `slug`, `sort_order`)
SELECT '题目讨论', 'problems', 30
WHERE NOT EXISTS (
  SELECT 1 FROM `t_forum_category` WHERE `slug` = 'problems'
);
