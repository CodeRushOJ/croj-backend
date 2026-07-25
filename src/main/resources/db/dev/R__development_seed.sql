INSERT INTO `t_problem_tag` (`name`, `color`) VALUES
  ('动态规划', '#67C23A'), ('贪心算法', '#E6A23C'), ('数组', '#409EFF'), ('图论', '#9B59B6')
ON DUPLICATE KEY UPDATE `color` = VALUES(`color`);

INSERT INTO `t_forum_category` (`name`, `slug`, `sort_order`) VALUES
  ('公告', 'announcements', 10), ('算法交流', 'algorithms', 20), ('题目讨论', 'problems', 30)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `sort_order` = VALUES(`sort_order`);
