// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;



contract ComplaintRegistry {

    struct ComplaintRecord {
        bytes32 hash;         // SHA-256 hash of complaint data
        uint256 timestamp;    // Block timestamp when filed
        address filedBy;      // Address that submitted the TX (backend wallet)
        bool exists;          // Guard against double-filing
    }

    // complaintId (from PostgreSQL) => on-chain record
    mapping(uint256 => ComplaintRecord) public complaints;

    // Total complaints registered on-chain
    uint256 public totalComplaints;

    // Contract owner (deployer / backend wallet)
    address public owner;

    // Events for frontend tracking & indexing
    event ComplaintFiled(
        uint256 indexed complaintId,
        bytes32 hash,
        uint256 timestamp,
        address indexed filedBy
    );

    event ComplaintVerified(
        uint256 indexed complaintId,
        bool isValid
    );

    modifier onlyOwner() {
        require(msg.sender == owner, "Only owner can call this");
        _;
    }

    constructor() {
        owner = msg.sender;
    }

    /// @notice File a complaint hash on-chain
    /// @param _complaintId The PostgreSQL complaint ID
    /// @param _hash SHA-256 hash of (id + title + description + location + timestamp)
    function fileComplaint(uint256 _complaintId, bytes32 _hash) external onlyOwner {
        require(!complaints[_complaintId].exists, "Complaint already filed on-chain");
        require(_hash != bytes32(0), "Hash cannot be empty");

        complaints[_complaintId] = ComplaintRecord({
            hash: _hash,
            timestamp: block.timestamp,
            filedBy: msg.sender,
            exists: true
        });

        totalComplaints++;

        emit ComplaintFiled(_complaintId, _hash, block.timestamp, msg.sender);
    }

    /// @notice Batch file multiple complaints in one TX (gas efficient)
    /// @param _ids Array of complaint IDs
    /// @param _hashes Array of corresponding SHA-256 hashes
    function batchFileComplaints(uint256[] calldata _ids, bytes32[] calldata _hashes) external onlyOwner {
        require(_ids.length == _hashes.length, "Arrays must be same length");
        require(_ids.length <= 50, "Max 50 per batch");

        for (uint256 i = 0; i < _ids.length; i++) {
            if (!complaints[_ids[i]].exists && _hashes[i] != bytes32(0)) {
                complaints[_ids[i]] = ComplaintRecord({
                    hash: _hashes[i],
                    timestamp: block.timestamp,
                    filedBy: msg.sender,
                    exists: true
                });
                totalComplaints++;
                emit ComplaintFiled(_ids[i], _hashes[i], block.timestamp, msg.sender);
            }
        }
    }

    /// @notice Verify a complaint hash matches what's stored on-chain
    /// @param _complaintId The complaint ID to verify
    /// @param _hash The hash to verify against
    /// @return isValid Whether the hash matches the on-chain record
    function verifyComplaint(uint256 _complaintId, bytes32 _hash) external view returns (bool isValid) {
        ComplaintRecord memory record = complaints[_complaintId];
        require(record.exists, "Complaint not found on-chain");
        return record.hash == _hash;
    }

    /// @notice Get full complaint record
    /// @param _complaintId The complaint ID
    /// @return hash The stored hash
    /// @return timestamp When it was filed on-chain
    /// @return filedBy The wallet that filed it
    function getComplaint(uint256 _complaintId) external view returns (
        bytes32 hash,
        uint256 timestamp,
        address filedBy
    ) {
        ComplaintRecord memory record = complaints[_complaintId];
        require(record.exists, "Complaint not found on-chain");
        return (record.hash, record.timestamp, record.filedBy);
    }

    /// @notice Check if a complaint exists on-chain
    /// @param _complaintId The complaint ID
    /// @return Whether the complaint has been filed
    function complaintExists(uint256 _complaintId) external view returns (bool) {
        return complaints[_complaintId].exists;
    }

    /// @notice Transfer ownership (e.g. rotate backend wallet)
    /// @param _newOwner New owner address
    function transferOwnership(address _newOwner) external onlyOwner {
        require(_newOwner != address(0), "Invalid address");
        owner = _newOwner;
    }
}
