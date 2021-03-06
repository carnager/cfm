package Cfm::Ui::Cli;
use strict;
use warnings FATAL => 'all';
use Moo;
use Log::Any qw($log);

use Cfm::Autowire;
use Cfm::Config;
use Cfm::Connector::Mpris2;
use Cfm::Import::CsvImporter;
use Cfm::Mb::MbService;
use Cfm::Playback::Playback;
use Cfm::Playback::PlaybackService;
use Cfm::Ui::Format::Formatter;
use Cfm::User::User;
use Cfm::User::UserService;

my %command_mapping = (
    list           => \&cmd_list,
    add            => \&cmd_add,
    'import-csv'   => \&cmd_import_csv,
    'record-mpris' => \&cmd_record_mpris,
    'create-user'  => \&cmd_create_user,
    now            => \&cmd_now,
    'find-rg'      => \&cmd_find_rg,
);

has loglevel => inject 'loglevel';

has config => singleton 'Cfm::Config';
has csv_importer => singleton 'Cfm::Import::CsvImporter';
has formatter => singleton 'Cfm::Ui::Format::Formatter';
has mbservice => singleton 'Cfm::Mb::MbService';
has mpris2 => singleton 'Cfm::Connector::Mpris2';
has playback_service => singleton 'Cfm::Playback::PlaybackService';
has user_service => singleton 'Cfm::User::UserService';

sub run {
    my ($self, @args) = @_;

    $self->config->add_flags(\@args);
    my ($cmd, $cmdargs) = $self->greedy_match_command(\@args);
    $log->debug("$cmd args: " . join " ", $cmdargs->@*);

    $command_mapping{$cmd}->($self, $cmdargs->@*);
}

sub greedy_match_command {
    my ($self, $command) = @_;
    my $c = scalar($command->@*);
    my $match = "";
    my $match_index = - 1;

    for (my $i = 0; $i < $c; $i++) {
        my $candidate = join "-", @{$command}[0 .. $i];
        if (defined $command_mapping{$candidate}) {
            $match_index = $i;
            $match = $candidate;
        }
    }
    if ($match_index >= 0) {
        return ($match, [ @{$command}[$match_index + 1 .. $c - 1] ])
    } else {
        die $log->error("No such command " . (join "-", $command->@*));
    }
}

### Commands ###

sub cmd_list {
    my ($self) = @_;

    # if the user specifies --acc, we use the service method to get accumulated broken playbacks, rather than the
    # regular list. --broken then has no effect on the result.
    if ($self->config->has_option('acc')) {
        my $acc_playbacks = $self->playback_service->accumulated_broken_playbacks($self->config->get_option('page') - 1);
        $self->formatter->accumulated_playbacks($acc_playbacks);
    } else {
        my $playbacks = $self->playback_service->my_playbacks($self->config->get_option('page') - 1,
            $self->config->get_option("broken"));
        $self->formatter->playback_list($playbacks);
    }
}

sub cmd_add {
    my ($self) = @_;

    my $kv = $self->config->kv_store();
    my $playback = Cfm::Playback::Playback->new(
        artists        => [ $kv->{artist}, $kv->{artist1}, $kv->{artist2}, $kv->{artist3} ],
        recordingTitle => $kv->{title},
        releaseTitle   => $kv->{release},
        timestamp      => $kv->{timestamp},
        playTime       => $kv->{playtime},
        trackLength    => $kv->{length},
        discNumber     => $kv->{disc},
        trackNumber    => $kv->{track},
        id             => $kv->{id},
        source         => $kv->{source},
    );
    my $response = $self->playback_service->create_playback($playback);
    $self->formatter->playback($response);
}

sub cmd_import_csv {
    my ($self, @args) = @_;

    for my $file (@args) {
        $log->info("Importing playbacks from $file");
        $self->csv_importer->import_csv($file);
    }
}

sub cmd_record_mpris {
    my ($self) = @_;

    my $player = $self->config->require_option("player");
    $self->loglevel->("info") unless $self->config->has_option("quiet");
    $self->mpris2->listen($player);
}

sub cmd_create_user {
    my ($self) = @_;

    my $kv = $self->config->kv_store();
    my $state;
    if (defined $kv->{state} && $kv->{state} eq "active") {
        $state = $Cfm::User::User::active;
    } else {
        $state = $Cfm::User::User::inactive;
    }
    my $user = Cfm::User::User->new(
        name       => $kv->{name} // $kv->{user},
        password   => $kv->{password} // $kv->{pass},
        state      => $state,
        systemUser => 0,
    );
    my $response = $self->user_service->create_user($user);
    $self->formatter->user($response);
}

sub cmd_now {
    my ($self) = @_;

    my $np = $self->playback_service->get_now_playing;
    $self->formatter->playback($np);
}

sub cmd_find_rg {
    my ($self, $rg, @artists) = @_;

    my $rgs = $self->mbservice->identify_release_group(\@artists, $rg, $self->config->get_option("page") - 1);
    $self->formatter->release_groups($rgs);
}

1;
