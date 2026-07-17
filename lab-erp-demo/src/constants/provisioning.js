export const PROVISION_ADMIN_USERNAMES = ['Zhangqi', 'guojianwen', 'jiaomiao'];

export const canAccessProvisioning = (username) => {
	const normalized = String(username || '')
		.trim()
		.toLowerCase();
	return PROVISION_ADMIN_USERNAMES.some(
		(item) => item.toLowerCase() === normalized,
	);
};
