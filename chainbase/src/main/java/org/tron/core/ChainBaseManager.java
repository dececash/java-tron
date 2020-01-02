package org.tron.core;

import static org.tron.core.config.Parameter.ChainConstant.BLOCK_PRODUCED_INTERVAL;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tron.common.utils.ForkController;
import org.tron.common.utils.Sha256Hash;
import org.tron.common.zksnark.MerkleContainer;
import org.tron.core.capsule.BlockCapsule;
import org.tron.core.capsule.BlockCapsule.BlockId;
import org.tron.core.db.BlockIndexStore;
import org.tron.core.db.BlockStore;
import org.tron.core.db.CommonStore;
import org.tron.core.db.DelegationService;
import org.tron.core.db.KhaosDatabase;
import org.tron.core.db.RecentBlockStore;
import org.tron.core.db.TransactionStore;
import org.tron.core.db2.core.ITronChainBase;
import org.tron.core.exception.BadItemException;
import org.tron.core.exception.ItemNotFoundException;
import org.tron.core.store.AccountIdIndexStore;
import org.tron.core.store.AccountIndexStore;
import org.tron.core.store.AccountStore;
import org.tron.core.store.AssetIssueStore;
import org.tron.core.store.AssetIssueV2Store;
import org.tron.core.store.CodeStore;
import org.tron.core.store.ContractStore;
import org.tron.core.store.DelegatedResourceAccountIndexStore;
import org.tron.core.store.DelegatedResourceStore;
import org.tron.core.store.DelegationStore;
import org.tron.core.store.DynamicPropertiesStore;
import org.tron.core.store.ExchangeStore;
import org.tron.core.store.ExchangeV2Store;
import org.tron.core.store.IncrementalMerkleTreeStore;
import org.tron.core.store.NullifierStore;
import org.tron.core.store.ProposalStore;
import org.tron.core.store.StorageRowStore;
import org.tron.core.store.TransactionHistoryStore;
import org.tron.core.store.TransactionRetStore;
import org.tron.core.store.VotesStore;
import org.tron.core.store.WitnessScheduleStore;
import org.tron.core.store.WitnessStore;
import org.tron.core.store.ZKProofStore;

@Slf4j(topic = "DB")
@Component
public class ChainBaseManager {

  // db store
  @Autowired
  @Getter
  private AccountStore accountStore;
  @Autowired
  @Getter
  private BlockStore blockStore;
  @Autowired
  @Getter
  private WitnessStore witnessStore;
  @Autowired
  @Getter
  private AssetIssueStore assetIssueStore;
  @Autowired
  @Getter
  private AssetIssueV2Store assetIssueV2Store;
  @Autowired
  @Getter
  private DynamicPropertiesStore dynamicPropertiesStore;
  @Autowired
  @Getter
  private BlockIndexStore blockIndexStore;
  @Autowired
  @Getter
  private AccountIdIndexStore accountIdIndexStore;
  @Autowired
  @Getter
  private AccountIndexStore accountIndexStore;
  @Autowired
  @Getter
  private WitnessScheduleStore witnessScheduleStore;
  @Autowired
  @Getter
  private VotesStore votesStore;
  @Autowired
  @Getter
  private ProposalStore proposalStore;
  @Autowired
  @Getter
  private ExchangeStore exchangeStore;
  @Autowired
  @Getter
  private ExchangeV2Store exchangeV2Store;
  @Autowired
  @Getter
  private CodeStore codeStore;
  @Autowired
  @Getter
  private ContractStore contractStore;
  @Autowired
  @Getter
  private DelegatedResourceStore delegatedResourceStore;
  @Autowired
  @Getter
  private DelegatedResourceAccountIndexStore delegatedResourceAccountIndexStore;
  @Autowired
  @Getter
  private StorageRowStore storageRowStore;
  @Autowired
  @Getter
  private NullifierStore nullifierStore;
  @Autowired
  @Getter
  private ZKProofStore proofStore;

  @Autowired
  @Getter
  private IncrementalMerkleTreeStore merkleTreeStore;

  @Getter
  @Setter
  private MerkleContainer merkleContainer;

  @Getter
  @Setter
  private DelegationService delegationService;

  @Autowired
  @Getter
  private DelegationStore delegationStore;

  @Autowired
  @Getter
  private KhaosDatabase khaosDb;

  @Autowired
  @Getter
  private CommonStore commonStore;

  @Autowired
  @Getter
  private TransactionStore transactionStore;
  @Autowired
  @Getter
  private TransactionRetStore transactionRetStore;
  @Autowired
  @Getter
  private RecentBlockStore recentBlockStore;
  @Autowired
  @Getter
  private TransactionHistoryStore transactionHistoryStore;

  protected BlockCapsule genesisBlock;

  @Getter
  private ForkController forkController = ForkController.instance();

  public void closeOneStore(ITronChainBase database) {
    logger.info("******** begin to close " + database.getName() + " ********");
    try {
      database.close();
    } catch (Exception e) {
      logger.info("failed to close  " + database.getName() + ". " + e);
    } finally {
      logger.info("******** end to close " + database.getName() + " ********");
    }
  }

  public synchronized BlockId getHeadBlockId() {
    return new BlockId(
        getDynamicPropertiesStore().getLatestBlockHeaderHash(),
        getDynamicPropertiesStore().getLatestBlockHeaderNumber());
  }

  public BlockId getSolidBlockId() {
    try {
      long num = this.getDynamicPropertiesStore().getLatestSolidifiedBlockNum();
      return getBlockIdByNum(num);
    } catch (Exception e) {
      return getGenesisBlockId();
    }
  }

  public BlockId getGenesisBlockId() {
    return this.genesisBlock.getBlockId();
  }

  /**
   * Get the block id from the number.
   */
  public BlockId getBlockIdByNum(final long num) throws ItemNotFoundException {
    return this.getBlockIndexStore().get(num);
  }

  public long getHeadBlockTimeStamp() {
    return getDynamicPropertiesStore().getLatestBlockHeaderTimestamp();
  }

  public long getHeadSlot() {
    return (getDynamicPropertiesStore().getLatestBlockHeaderTimestamp() - getGenesisBlock()
        .getTimeStamp()) / BLOCK_PRODUCED_INTERVAL;
  }

  public BlockCapsule getGenesisBlock() {
    return genesisBlock;
  }


  public BlockCapsule getBlockByNum(final long num) throws
      ItemNotFoundException, BadItemException {
    return getBlockById(getBlockIdByNum(num));
  }

  /**
   * Get a BlockCapsule by id.
   */
  public BlockCapsule getBlockById(final Sha256Hash hash)
      throws BadItemException, ItemNotFoundException {
    BlockCapsule block = this.khaosDb.getBlock(hash);
    if (block == null) {
      block = this.getBlockStore().get(hash.getBytes());
    }
    return block;
  }

  public AssetIssueStore getAssetIssueStoreFinal() {
    if (getDynamicPropertiesStore().getAllowSameTokenName() == 0) {
      return getAssetIssueStore();
    } else {
      return getAssetIssueV2Store();
    }
  }

  public ExchangeStore getExchangeStoreFinal() {
    if (getDynamicPropertiesStore().getAllowSameTokenName() == 0) {
      return getExchangeStore();
    } else {
      return getExchangeV2Store();
    }
  }

  public void closeAllStore() {
    closeOneStore(transactionRetStore);
    closeOneStore(recentBlockStore);
    closeOneStore(transactionHistoryStore);
    closeOneStore(transactionStore);
    closeOneStore(accountStore);
    closeOneStore(blockStore);
    closeOneStore(blockIndexStore);
    closeOneStore(accountIdIndexStore);
    closeOneStore(accountIndexStore);
    closeOneStore(witnessScheduleStore);
    closeOneStore(assetIssueStore);
    closeOneStore(dynamicPropertiesStore);
    closeOneStore(codeStore);
    closeOneStore(contractStore);
    closeOneStore(storageRowStore);
    closeOneStore(exchangeStore);
    closeOneStore(proposalStore);
    closeOneStore(votesStore);
    closeOneStore(delegatedResourceStore);
    closeOneStore(delegatedResourceAccountIndexStore);
    closeOneStore(assetIssueV2Store);
    closeOneStore(exchangeV2Store);
    closeOneStore(nullifierStore);
    closeOneStore(merkleTreeStore);
    closeOneStore(delegationStore);
    closeOneStore(proofStore);
    closeOneStore(commonStore);
  }
}
